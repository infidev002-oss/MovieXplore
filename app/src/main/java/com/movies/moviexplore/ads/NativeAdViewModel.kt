package com.movies.moviexplore.ads

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.movies.moviexplore.config.RemoteValues
import com.movies.moviexplore.hooks.AdRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds one native ad for a screen. Nothing reloads by itself (no timers): call refreshIfNeeded(unitKey)
 * only when the screen is visible — onResume() when not hidden, and onHiddenChanged(false) for tabs
 * switched with hide/show. It loads a new ad only if there is none or the current one is older than
 * AdRules.nativeAdInterval(). Collect [ad] and bind it with NativeAdBinder.
 * The ad is destroyed when the ViewModel is cleared; an ad arriving after that is destroyed at once.
 */
class NativeAdViewModel(application: Application) : AndroidViewModel(application) {

    /** LOADING: first ad on its way (show a placeholder) · LOADED: [ad] is set · NONE: no ad (hide the slot). */
    enum class Status { LOADING, LOADED, NONE }

    private val _status = MutableStateFlow(Status.LOADING)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _ad = MutableStateFlow<NativeAd?>(null)
    val ad: StateFlow<NativeAd?> = _ad.asStateFlow()

    private var loading = false
    private var loadedAt = 0L
    private var cleared = false

    fun refreshIfNeeded(unitKey: String) {
        if (loading || cleared) return
        val context = getApplication<Application>()
        if (!AdsConsent.canRequestAds(context) || !RemoteValues.isAdUnitEnabled(unitKey)) { noAd(); return }
        val fresh = System.currentTimeMillis() - loadedAt < AdRules.nativeAdInterval()
        if (_ad.value != null && fresh) return
        val ids = RemoteValues.adUnitIds(unitKey)
        if (ids.isEmpty()) { noAd(); return }
        loading = true
        load(ids, 0)
    }

    private fun load(ids: List<String>, index: Int) {
        if (index >= ids.size) { loading = false; noAd(); return }
        val options = NativeAdOptions.Builder()
            .setVideoOptions(VideoOptions.Builder().setStartMuted(true).build())
            .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
            .build()
        AdLoader.Builder(getApplication(), ids[index])
            .withNativeAdOptions(options)
            .forNativeAd { newAd ->
                if (cleared) { newAd.destroy(); return@forNativeAd }
                val old = _ad.value
                _ad.value = newAd
                old?.destroy()
                loadedAt = System.currentTimeMillis()
                loading = false
                _status.value = Status.LOADED
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (cleared) { loading = false; return }
                    load(ids, index + 1)
                }
                override fun onAdClicked() { AdState.markAdClicked() }
            })
            .build()
            .loadAd(AdRequest.Builder().build())
    }

    // A failed reload keeps the current ad on screen.
    private fun noAd() { if (_ad.value == null) _status.value = Status.NONE }

    override fun onCleared() {
        cleared = true
        _ad.value?.destroy()
        _ad.value = null
        super.onCleared()
    }
}
