package com.movies.moviexplore.ui.profile

import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Outline
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import com.google.firebase.auth.AuthCredential
import com.movies.moviexplore.R
import com.movies.moviexplore.ads.NativeAds
import com.movies.moviexplore.ads.NativeSize
import com.movies.moviexplore.config.RemoteValues
import com.movies.moviexplore.data.ContentLocale
import com.movies.moviexplore.data.MediaRepository
import com.movies.moviexplore.data.SettingsRepository
import com.movies.moviexplore.ui.common.ImageLoader
import com.movies.moviexplore.ui.common.inflate
import com.movies.moviexplore.ui.common.isOnline
import com.movies.moviexplore.ui.common.openLink
import com.movies.moviexplore.ui.common.queueAppToast
import com.movies.moviexplore.ui.common.show
import com.movies.moviexplore.ui.common.showAppToast
import com.movies.moviexplore.ui.common.showExpandableSheet
import com.movies.moviexplore.ui.common.showPendingAppToast
import com.movies.moviexplore.ui.common.toast
import com.movies.moviexplore.ui.main.TabFragment
import com.movies.moviexplore.ui.onboarding.ChoiceList
import com.movies.moviexplore.ui.onboarding.ContentChoices
import com.movies.moviexplore.ui.signin.clearGoogleCredential
import com.movies.moviexplore.ui.signin.getGoogleCredential
import kotlinx.coroutines.launch

/** Account tab: sign-in card or the signed-in user, content language and country, app links, and the TMDB attribution. */
class ProfileFragment : TabFragment(R.layout.tab_profile) {

    private lateinit var store: ProfileStore
    private val settingsNative by lazy { NativeAds.viewModel(this, RemoteValues.NATIVE_SETTINGS) }

    private lateinit var root: View
    private lateinit var scroll: NestedScrollView
    private lateinit var scrim: View
    private lateinit var signIn: View
    private lateinit var googleSignIn: View
    private lateinit var user: View
    private lateinit var rows: LinearLayout
    private lateinit var content: View

    private var deleteDialog: AlertDialog? = null
    private var deletingDialog: AlertDialog? = null
    private var choiceDialog: Dialog? = null
    private var renderedSignedIn: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ProfileStore()
    }

    override fun onTabViewCreated(view: View) {
        root = view
        scroll = view.findViewById(R.id.profile_scroll)
        scrim = view.findViewById(R.id.profile_status_scrim)
        signIn = view.findViewById(R.id.profile_sign_in)
        signIn.clipToOutline = true   // the posters follow the card's rounded corners
        user = view.findViewById(R.id.profile_user)
        rows = view.findViewById(R.id.profile_rows)
        content = view.findViewById(R.id.profile_content)
        NativeAds.attach(viewLifecycleOwner, settingsNative, view.findViewById(R.id.profile_native), RemoteValues.NATIVE_SETTINGS, NativeSize.SMALL)
        // A new view starts without rows, so render() builds them again.
        renderedSignedIn = null

        val activity = requireActivity()
        scroll.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, y, _, _ ->
            val target = if (y > 0) 1f else 0f
            if (scrim.alpha != target) scrim.animate().alpha(target).setDuration(150).start()
        })
        googleSignIn = view.findViewById(R.id.profile_google_sign_in)
        googleSignIn.setOnClickListener { store.onIntent(ProfileIntent.SignInClicked) }
        view.findViewById<ImageView>(R.id.profile_photo).apply {
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) = outline.setOval(0, 0, view.width, view.height)
            }
            clipToOutline = true
        }
        view.findViewById<View>(R.id.profile_tmdb).setOnClickListener { activity.openLink("https://www.themoviedb.org") }
        scroll.addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ -> fitContentWidth(right - left) }

        viewScope.launch { store.state.collect { render(it) } }
        viewScope.launch { store.effects.collect { handle(it) } }
        // The language / country confirmation queued before the rebuild.
        activity.showPendingAppToast()
    }

    // The account may have changed while the tab was away (e.g. a deleted or expired sign-in).
    override fun onResume() {
        super.onResume()
        if (!isHidden) {
            store.onIntent(ProfileIntent.Refresh)
            settingsNative.refreshIfNeeded(RemoteValues.NATIVE_SETTINGS)
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            store.onIntent(ProfileIntent.Refresh)
            settingsNative.refreshIfNeeded(RemoteValues.NATIVE_SETTINGS)
        }
    }

    private fun render(state: ProfileUiState) {
        val account = state.account
        signIn.show(account == null)
        if (account == null) renderPosters(state.posters)
        googleSignIn.isEnabled = !state.isSigningIn
        user.show(account != null)
        if (account != null) renderAccount(account)
        if (renderedSignedIn != (account != null)) {
            renderedSignedIn = account != null
            buildRows(signedIn = account != null)
        }

        if (state.confirmDelete && deleteDialog == null) {
            val activity = requireActivity()
            deleteDialog = AlertDialog.Builder(activity)
                .setTitle("Delete account?")
                .setMessage(
                    "This permanently deletes your MovieXplore account, your profile and your watchlist, " +
                        "on this phone and in the cloud, and signs you out. This can't be undone."
                )
                .setPositiveButton("Delete") { _, _ ->
                    // Offline, Firebase would wait for a connection with no end, so don't start.
                    if (activity.isOnline()) {
                        store.onIntent(ProfileIntent.ConfirmDelete)
                    } else {
                        activity.showAppToast("No internet connection. Connect and try again.", R.drawable.ic_info)
                    }
                }
                .setNegativeButton("Cancel", null)
                .setOnDismissListener {
                    deleteDialog = null
                    store.onIntent(ProfileIntent.DismissDelete)
                }
                .show()
        } else if (!state.confirmDelete) {
            deleteDialog?.dismiss()
        }

        // Blocks the tab (Logout, Delete again, Back) until AuthManager reports back.
        if (state.isDeleting && deletingDialog == null) {
            deletingDialog = deletingDialog()
        } else if (!state.isDeleting) {
            deletingDialog?.dismiss()
            deletingDialog = null
        }
    }

    /** The sign-in panel's poster backdrop: one trending poster per slot. */
    private fun renderPosters(posters: List<String>) {
        val slots = root.findViewById<ViewGroup>(R.id.profile_sign_in_posters)
        for (i in 0 until slots.childCount) {
            val slot = slots.getChildAt(i) as ImageView
            val url = posters.getOrNull(i)
            // Re-renders happen on every visit: only load a slot when its poster changed.
            if (url != null && slot.getTag(R.id.image_url) != url) ImageLoader.load(slot, url)
        }
    }

    /** The top panel's user: Google photo over the name's initial, name, and email. */
    private fun renderAccount(account: Account) {
        root.findViewById<TextView>(R.id.profile_name).text = account.name
        root.findViewById<TextView>(R.id.profile_email).apply {
            text = account.email
            show(!account.email.isNullOrBlank() && account.email != account.name)
        }
        root.findViewById<TextView>(R.id.profile_initial).text =
            account.name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
        val photo = root.findViewById<ImageView>(R.id.profile_photo)
        // Refresh runs on every visit: only reload when the photo actually changed.
        if (photo.getTag(R.id.image_url) != account.photoUrl) ImageLoader.load(photo, account.photoUrl)
        user.contentDescription = listOfNotNull("Signed in as ${account.name}", account.email).joinToString(", ")
    }

    /** "Deleting account…" with a spinner (layout: dialog_deleting.xml). */
    private fun deletingDialog(): AlertDialog =
        AlertDialog.Builder(requireActivity()).setView(R.layout.dialog_deleting).setCancelable(false).show()

    private fun handle(effect: ProfileEffect) {
        val activity = requireActivity()
        when (effect) {
            ProfileEffect.PickGoogleAccount -> viewScope.launch {
                var credential: AuthCredential? = null
                try {
                    credential = getGoogleCredential(activity)
                } finally {
                    // Also when the view goes away mid-picker, so the button never stays disabled.
                    store.onIntent(ProfileIntent.SignInCredential(credential))
                }
            }
            is ProfileEffect.SignedIn -> activity.showAppToast("Signed in as ${effect.name}")
            ProfileEffect.ClearCredential -> {
                val app = activity.application
                viewScope.launch { clearGoogleCredential(app) }
            }
            ProfileEffect.Reauthenticate -> viewScope.launch {
                activity.toast("Confirm your Google account to delete it")
                store.onIntent(ProfileIntent.Reauthenticated(getGoogleCredential(activity)))
            }
            ProfileEffect.AccountDeleted -> {
                scroll.scrollTo(0, 0)
                activity.showAppToast("Your account has been deleted")
            }
            is ProfileEffect.Message -> activity.showAppToast(effect.text, R.drawable.ic_info)
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
        choiceDialog = activity.showExpandableSheet(R.layout.dialog_content_choice, onDismiss = { choiceDialog = null }) { dialog, sheet ->
            sheet.findViewById<TextView>(R.id.choice_sheet_title).text = if (language) "Language" else "Country"
            sheet.findViewById<TextView>(R.id.choice_sheet_subtitle).text = if (language) {
                "Movie and TV titles and descriptions appear in this language when available."
            } else {
                "Popular lists, releases and where to watch match this country."
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
        // Shown by the rebuilt screen: one shown now would vanish with recreate().
        if (language) {
            queueAppToast("Language: ${ContentChoices.languageName(code)}", R.drawable.ic_language)
        } else {
            queueAppToast("Country: ${ContentChoices.countryName(code)}", R.drawable.ic_country)
        }
        viewScope.launch {
            if (language) MediaRepository.shared.clearLanguageCache()
            // MainActivity restores this tab after the rebuild.
            activity.recreate()
        }
    }

    /**
     * Keeps the column readable in landscape and on wide screens: full width in portrait, capped and centred
     * beyond. Both sizes are sdp, so the cap grows with the rest of the layout on tablets.
     */
    private fun fitContentWidth(width: Int) {
        val res = root.resources
        val minSide = res.getDimensionPixelSize(com.intuit.sdp.R.dimen._13sdp)
        val maxWidth = res.getDimensionPixelSize(com.intuit.sdp.R.dimen._400sdp)
        val side = maxOf(minSide, (width - maxWidth) / 2)
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

    override fun applyTopSpace(view: View, px: Int) {
        view.findViewById<View>(R.id.profile_title).padTop(px)
        fitStatusScrim(scrim, px)
    }

    override fun onDestroyView() {
        deleteDialog?.dismiss()
        deletingDialog?.dismiss()
        deletingDialog = null
        choiceDialog?.dismiss()
        super.onDestroyView()
    }

    override fun onDestroy() {
        store.clear()
        super.onDestroy()
    }
}
