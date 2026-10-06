package com.movies.myapplication.ui.profile

import android.accounts.AccountManager
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import com.movies.myapplication.R
import com.movies.myapplication.appContainer
import com.movies.myapplication.ui.common.dp
import com.movies.myapplication.ui.common.inflate
import com.movies.myapplication.ui.common.onSystemBars
import com.movies.myapplication.ui.common.openLink
import com.movies.myapplication.ui.common.padForSystemBars
import com.movies.myapplication.ui.common.show
import com.movies.myapplication.ui.common.showBottomSheet
import com.movies.myapplication.ui.common.toast
import com.movies.myapplication.ui.main.TabFragment
import kotlinx.coroutines.launch

/** Account tab: sign-in card or the signed-in user, app links, and the TMDB attribution. */
class ProfileFragment : TabFragment(R.layout.tab_profile) {

    private lateinit var store: ProfileStore

    private lateinit var root: View
    private lateinit var scroll: ScrollView
    private lateinit var scrim: View
    private lateinit var signIn: View
    private lateinit var user: View
    private lateinit var rows: LinearLayout
    private lateinit var content: View

    private var loginSheet: Dialog? = null
    private var deleteDialog: AlertDialog? = null
    private var renderedSignedIn: Boolean? = null

    /** Android's built-in picker for the Google accounts already on the phone; no extra library needed. */
    private val pickAccount = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val email = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME).takeIf { result.resultCode == Activity.RESULT_OK }
        store.onIntent(ProfileIntent.GoogleAccountPicked(email))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = requireContext().appContainer
        store = ProfileStore(container.account, container.watchlist)
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
        signIn.setOnClickListener { store.onIntent(ProfileIntent.OpenLogin) }
        view.findViewById<View>(R.id.profile_tmdb).setOnClickListener { activity.openLink("https://www.themoviedb.org") }
        scroll.addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ -> fitContentWidth(right - left) }

        viewScope.launch { store.state.collect { render(it) } }
        viewScope.launch {
            store.effects.collect { effect ->
                when (effect) {
                    ProfileEffect.PickGoogleAccount -> pickGoogleAccount()
                }
            }
        }
    }

    private fun render(state: ProfileUiState) {
        val activity = requireActivity()
        val account = state.account
        signIn.show(account == null)
        user.show(account != null)
        if (account != null) root.findViewById<TextView>(R.id.profile_name).text = account.name
        if (renderedSignedIn != (account != null)) {
            renderedSignedIn = account != null
            buildRows(signedIn = account != null)
        }

        if (state.showLogin && loginSheet == null) {
            loginSheet = activity.showBottomSheet(R.layout.dialog_login, onDismiss = {
                loginSheet = null
                store.onIntent(ProfileIntent.DismissLogin)
            }) { _, sheet ->
                sheet.findViewById<View>(R.id.login_google).setOnClickListener { store.onIntent(ProfileIntent.ContinueWithGoogle) }
                sheet.findViewById<View>(R.id.login_guest).setOnClickListener { store.onIntent(ProfileIntent.ContinueAsGuest) }
            }
        } else if (!state.showLogin) {
            loginSheet?.dismiss()
        }

        if (state.confirmDelete && deleteDialog == null) {
            deleteDialog = AlertDialog.Builder(activity)
                .setTitle("Delete account?")
                .setMessage("This signs you out and permanently removes your watchlist and profile from this phone.")
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

    private fun buildRows(signedIn: Boolean) {
        val activity = requireActivity()
        rows.removeAllViews()
        addRow(R.drawable.ic_thumb_up, "Rate Us") {
            val pkg = activity.packageName
            activity.openLink("market://details?id=$pkg", "https://play.google.com/store/apps/details?id=$pkg")
        }
        addRow(R.drawable.ic_privacy, "Privacy Policy") { openConfiguredLink(R.string.privacy_policy_url) }
        addRow(R.drawable.ic_terms, "Terms & Conditions") { openConfiguredLink(R.string.terms_url) }
        addRow(R.drawable.ic_feedback, "Feedback") { sendFeedback() }
        if (signedIn) {
            addRow(R.drawable.ic_logout, "Logout") { store.onIntent(ProfileIntent.Logout) }
            addRow(R.drawable.ic_person_remove, "Delete Account") { store.onIntent(ProfileIntent.RequestDelete) }
        }
    }

    private fun addRow(icon: Int, label: String, onClick: () -> Unit) {
        val row = rows.inflate(R.layout.item_account_row)
        row.findViewById<ImageView>(R.id.account_row_icon).setImageResource(icon)
        row.findViewById<TextView>(R.id.account_row_label).text = label
        row.setOnClickListener { onClick() }
        rows.addView(row)
    }

    /** Keeps the column readable on tablets and in landscape: full width on phones, capped and centred beyond. */
    private fun fitContentWidth(width: Int) {
        val side = maxOf(root.dp(16), (width - root.dp(MAX_CONTENT_WIDTH_DP)) / 2)
        if (content.paddingStart != side) content.setPaddingRelative(side, 0, side, 0)
    }

    private fun pickGoogleAccount() {
        val intent = AccountManager.newChooseAccountIntent(null, null, arrayOf("com.google"), null, null, null, null)
        runCatching { pickAccount.launch(intent) }
            .onFailure { requireActivity().toast("Google sign-in isn't available on this phone. Continue as a guest for now.", long = true) }
    }

    private fun openConfiguredLink(urlRes: Int) {
        val activity = requireActivity()
        val url = activity.getString(urlRes)
        if (url.isBlank()) activity.toast("Not available yet") else activity.openLink(url)
    }

    private fun sendFeedback() {
        val activity = requireActivity()
        val email = activity.getString(R.string.feedback_email)
        if (email.isBlank()) return activity.toast("Not available yet")
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            .putExtra(Intent.EXTRA_SUBJECT, "Feedback: ${activity.getString(R.string.app_name)}")
        runCatching { activity.startActivity(intent) }
    }

    override fun onScrollToTop() = scroll.scrollTo(0, 0)

    override fun applyBottomSpace(px: Int) {
        scroll.setPadding(0, 0, 0, px)
    }

    override fun onDestroyView() {
        loginSheet?.dismiss()
        deleteDialog?.dismiss()
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
