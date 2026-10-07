package com.movies.moviexplore.playstore

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.bumptech.glide.Glide
import com.movies.moviexplore.R
import com.movies.moviexplore.config.RemoteValues

/**
 * Blocking force-update dialog (Remote Config: min_supported_version + Update_App {appTitle, appDesc,
 * appIcon, appURL}). A DialogFragment, so it survives rotation; it can't be dismissed.
 *
 * Splash, after Remote Config is fetched:
 *   if (ForceUpdate.isRequired(this)) { ForceUpdate.show(this); return }
 */
object ForceUpdate {
    private const val TAG = "playstore_force_update"

    fun isRequired(context: Context): Boolean =
        PlayStore.versionCode(context) < RemoteValues.minSupportedVersion

    fun show(activity: FragmentActivity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val fm = activity.supportFragmentManager
        if (fm.isStateSaved || fm.findFragmentByTag(TAG) != null) return   // already showing (e.g. after rotation)
        try { ForceUpdateDialog().show(fm, TAG) } catch (_: Exception) { }
    }
}

class ForceUpdateDialog : DialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        super.onCreateDialog(savedInstanceState).apply {
            setCanceledOnTouchOutside(false)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.playstore_force_update, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val json = RemoteValues.updateDialog()
        val title = json?.optString("appTitle").orEmpty()
        val message = json?.optString("appDesc").orEmpty()
        view.findViewById<TextView>(R.id.playstore_update_title).text =
            title.ifBlank { getString(R.string.playstore_update_title) }
        view.findViewById<TextView>(R.id.playstore_update_message).text =
            message.ifBlank { getString(R.string.playstore_update_message) }

        val icon = view.findViewById<ImageView>(R.id.playstore_update_icon)
        val iconUrl = json?.optString("appIcon").orEmpty()
        val context = requireContext()
        try {
            if (iconUrl.isNotBlank()) Glide.with(this).load(iconUrl).into(icon)
            else icon.setImageDrawable(context.packageManager.getApplicationIcon(context.applicationInfo))
        } catch (_: Exception) { }

        view.findViewById<View>(R.id.playstore_update_button).setOnClickListener {
            val activity = activity ?: return@setOnClickListener
            PlayStore.open(activity, json?.optString("appURL"))
            activity.finishAffinity()
        }
    }
}
