package com.movies.moviexplore.ui.profile

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import com.movies.moviexplore.R
import com.movies.moviexplore.ads.NativeAds
import com.movies.moviexplore.ads.NativeSize
import com.movies.moviexplore.config.RemoteValues
import com.movies.moviexplore.data.ContentLocale
import com.movies.moviexplore.data.MediaRepository
import com.movies.moviexplore.data.SettingsRepository
import com.movies.moviexplore.ui.common.dp
import com.movies.moviexplore.ui.common.inflate
import com.movies.moviexplore.ui.common.onSystemBars
import com.movies.moviexplore.ui.common.openLink
import com.movies.moviexplore.ui.common.padForSystemBars
import com.movies.moviexplore.ui.common.show
import com.movies.moviexplore.ui.common.showBottomSheet
import com.movies.moviexplore.ui.common.toast
import com.movies.moviexplore.ui.main.TabFragment
import com.movies.moviexplore.ui.onboarding.ChoiceList
import com.movies.moviexplore.ui.onboarding.ContentChoices
import com.movies.moviexplore.ui.signin.SignInActivity
import com.movies.moviexplore.ui.signin.clearGoogleCredential
import com.movies.moviexplore.ui.signin.getGoogleCredential
import kotlinx.coroutines.launch

/** Account tab: sign-in card or the signed-in user, content language and country, app links, and the TMDB attribution. */
class ProfileFragment : TabFragment(R.layout.tab_profile) {

    private lateinit var store: ProfileStore

    private lateinit var root: View
    private lateinit var scroll: ScrollView
    private lateinit var scrim: View
    private lateinit var signIn: View
    private lateinit var user: View
    private lateinit var rows: LinearLayout
    private lateinit var content: View

    private var deleteDialog: AlertDialog? = null
    private var choiceDialog: Dialog? = null
    private var renderedSignedIn: Boolean? = null

    private val settingsNative by lazy { NativeAds.viewModel(this, RemoteValues.NATIVE_SETTINGS) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ProfileStore()
    }

    override fun onTabViewCreated(view: View) {
        root = view
        scroll = view.findViewById(R.id.profile_scroll)
        scrim = view.findViewById(R.id.profile_status_scrim)
        signIn = view.findViewById(R.id.profile_sign_in)
        user = view.findViewById(R.id.profile_user)
        rows = view.findViewById(R.id.profile_rows)
        content = view.findViewById(R.id.profile_content)
        // A new view starts without rows, so render() builds them again.
        renderedSignedIn = null

        val activity = requireActivity()
        view.findViewById<View>(R.id.profile_title).padForSystemBars(top = true)
        scrim.onSystemBars { bars -> scrim.layoutParams = scrim.layoutParams.apply { height = bars.top + view.dp(16) } }
        scroll.setOnScrollChangeListener { _, _, y, _, _ ->
            val target = if (y > 0) 1f else 0f
            if (scrim.alpha != target) scrim.animate().alpha(target).setDuration(150).start()
        }
        // MainActivity's Back handler returns to Home from this tab.
        view.findViewById<View>(R.id.profile_back).setOnClickListener { activity.onBackPressedDispatcher.onBackPressed() }
        signIn.setOnClickListener { store.onIntent(ProfileIntent.OpenSignIn) }
        view.findViewById<View>(R.id.profile_tmdb).setOnClickListener { activity.openLink("https://www.themoviedb.org") }
        scroll.addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ -> fitContentWidth(right - left) }
        NativeAds.attach(viewLifecycleOwner, settingsNative, view.findViewById(R.id.profile_native), RemoteValues.NATIVE_SETTINGS, NativeSize.SMALL)

        viewScope.launch { store.state.collect { render(it) } }
        viewScope.launch { store.effects.collect { handle(it) } }
    }

    // The user may have just signed in on the sign-in screen.
    override fun onResume() {
        super.onResume()
        if (!isHidden) {
            store.onIntent(ProfileIntent.Refresh)
            settingsNative.refreshIfNeeded(RemoteValues.NATIVE_SETTINGS)
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) store.onIntent(ProfileIntent.Refresh)
        if (!hidden && isResumed) settingsNative.refreshIfNeeded(RemoteValues.NATIVE_SETTINGS)
    }

    private fun render(state: ProfileUiState) {
        val account = state.account
        signIn.show(account == null)
        user.show(account != null)
        if (account != null) root.findViewById<TextView>(R.id.profile_name).text = account.name
        if (renderedSignedIn != (account != null)) {
            renderedSignedIn = account != null
            buildRows(signedIn = account != null)
        }

        if (state.confirmDelete && deleteDialog == null) {
            deleteDialog = AlertDialog.Builder(requireActivity())
                .setTitle("Delete account?")
                .setMessage("This permanently deletes your account and the watchlist saved to it, and signs you out.")
                .setPositiveButton("Delete") { _, _ -> store.onIntent(ProfileIntent.ConfirmDelete) }
                .setNegativeButton("Cancel", null)
                .setOnDismissListener {
                    deleteDialog = null
                    store.onIntent(ProfileIntent.DismissDelete)
                }
                .show()
        } else if (!state.confirmDelete) {
            deleteDialog?.dismiss()
        }
    }

    private fun handle(effect: ProfileEffect) {
        val activity = requireActivity()
        when (effect) {
            ProfileEffect.OpenSignIn -> startActivity(Intent(activity, SignInActivity::class.java))
            ProfileEffect.ClearCredential -> {
                val app = activity.application
                viewScope.launch { clearGoogleCredential(app) }
            }
            ProfileEffect.Reauthenticate -> viewScope.launch {
                activity.toast("Confirm your Google account to delete it")
                store.onIntent(ProfileIntent.Reauthenticated(getGoogleCredential(activity)))
            }
            is ProfileEffect.Message -> activity.toast(effect.text, long = true)
        }
    }

    private fun buildRows(signedIn: Boolean) {
        rows.removeAllViews()
        addRow(R.drawable.ic_language, "Language", ContentChoices.languageName(ContentLocale.language ?: "en")) {
            openContentChoice(language = true)
        }
        addRow(R.drawable.ic_country, "Country", ContentChoices.countryName(ContentLocale.country)) {
            openContentChoice(language = false)
        }
        addRow(R.drawable.ic_privacy, "Privacy Policy") { openConfiguredLink(RemoteValues.privacyPolicyUrl) }
        addRow(R.drawable.ic_terms, "Terms & Conditions") { openConfiguredLink(RemoteValues.termsUrl) }
        if (signedIn) {
            addRow(R.drawable.ic_logout, "Logout") { store.onIntent(ProfileIntent.Logout) }
            addRow(R.drawable.ic_person_remove, "Delete Account") { store.onIntent(ProfileIntent.RequestDelete) }
        }
    }

    /** A settings card; [value] (e.g. the current language) shows on the right, before the chevron. */
    private fun addRow(icon: Int, label: String, value: String? = null, onClick: () -> Unit) {
        val row = rows.inflate(R.layout.item_account_row)
        row.findViewById<ImageView>(R.id.account_row_icon).setImageResource(icon)
        row.findViewById<TextView>(R.id.account_row_label).text = label
        row.findViewById<TextView>(R.id.account_row_value).apply {
            text = value
            show(value != null)
        }
        row.contentDescription = listOfNotNull(label, value).joinToString(", ")
        row.setOnClickListener { onClick() }
        rows.addView(row)
    }

    /** The content language or country as a bottom sheet of the onboarding cards; tapping one applies it. */
    private fun openContentChoice(language: Boolean) {
        val activity = requireActivity()
        val current = if (language) ContentLocale.language ?: "en" else ContentLocale.country
        choiceDialog = activity.showBottomSheet(R.layout.dialog_content_choice, onDismiss = { choiceDialog = null }) { dialog, sheet ->
            sheet.findViewById<TextView>(R.id.choice_sheet_title).text = if (language) "Language" else "Country"
            sheet.findViewById<TextView>(R.id.choice_sheet_subtitle).text = if (language) {
                "Movie and TV titles and descriptions appear in this language when available."
            } else {
                "Popular lists, releases and where to watch match this country."
            }
            // A fixed height keeps the sheet steady while the country search narrows the list.
            sheet.findViewById<ListView>(R.id.choice_list).apply {
                layoutParams = layoutParams.apply { height = (resources.displayMetrics.heightPixels * 0.55f).toInt() }
            }
            val options = if (language) ContentChoices.languages() else ContentChoices.countries()
            ChoiceList(sheet, options, current, searchable = !language) { code ->
                dialog.dismiss()
                if (code != current) applyContentChoice(language, code)
            }
        }
    }

    /** Saves the pick, then rebuilds the main screen so every tab reloads in the new language / country. */
    private fun applyContentChoice(language: Boolean, code: String) {
        val activity = requireActivity()
        val settings = SettingsRepository.get(activity)
        if (language) settings.setContentLanguage(code) else settings.setCountry(code)
        activity.toast(if (language) "Language: ${ContentChoices.languageName(code)}" else "Country: ${ContentChoices.countryName(code)}")
        viewScope.launch {
            if (language) MediaRepository.shared.clearLanguageCache()
            // MainActivity restores this tab after the rebuild.
            activity.recreate()
        }
    }

    /** Keeps the column readable on tablets and in landscape: full width on phones, capped and centred beyond. */
    private fun fitContentWidth(width: Int) {
        val side = maxOf(root.dp(16), (width - root.dp(MAX_CONTENT_WIDTH_DP)) / 2)
        if (content.paddingStart != side) content.setPaddingRelative(side, 0, side, 0)
    }

    /** Privacy policy and terms links come from Remote Config (ppLink / tncLink). */
    private fun openConfiguredLink(url: String) {
        val activity = requireActivity()
        if (url.isBlank()) activity.toast("Not available yet") else activity.openLink(url)
    }

    override fun onScrollToTop() = scroll.scrollTo(0, 0)

    override fun applyBottomSpace(px: Int) {
        scroll.setPadding(0, 0, 0, px)
    }

    override fun onDestroyView() {
        deleteDialog?.dismiss()
        choiceDialog?.dismiss()
        super.onDestroyView()
    }

    override fun onDestroy() {
        store.clear()
        super.onDestroy()
    }

    private companion object {
        const val MAX_CONTENT_WIDTH_DP = 600
    }
}
