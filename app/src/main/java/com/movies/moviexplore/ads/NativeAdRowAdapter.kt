package com.movies.moviexplore.ads

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.nativead.NativeAd
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * One-row adapter: a native ad as the first item of a list. Put it first in a ConcatAdapter:
 *
 *   private val categoryNative by lazy { NativeAds.viewModel(this, RemoteValues.NATIVE_CATEGORY) }
 *   private lateinit var nativeRow: NativeAdRowAdapter
 *
 *   onCreate / onViewCreated:
 *     nativeRow = NativeAdRowAdapter(lifecycleOwner, categoryNative, RemoteValues.NATIVE_CATEGORY, NativeSize.MEDIUM,
 *         marginDp = 10)   // spacing around the ad, your design (default 0: the kit adds none)
 *     recyclerView.adapter = ConcatAdapter(nativeRow, postersAdapter)
 *     // grid: (layoutManager as GridLayoutManager).spanSizeLookup = nativeRow.fullSpanLookup(spanCount)
 *   onResume (when visible) and onHiddenChanged(false):
 *     nativeRow.onScreenVisible()
 *
 * The row shows a placeholder while the first ad loads, then the ad, and disappears when there is no ad.
 * Refresh: one per visit to the screen — now if the row is on screen, otherwise when the user scrolls to it.
 * Scrolling the row off and back on just shows the same ad again (no new request).
 */
class NativeAdRowAdapter(
    owner: LifecycleOwner,
    private val viewModel: NativeAdViewModel,
    private val unitKey: String,
    private val size: NativeSize = NativeSize.MEDIUM,
    private val marginDp: Int = 0                    // spacing around the ad on every side (the kit adds none)
) : RecyclerView.Adapter<NativeAdRowAdapter.Holder>() {

    class Holder(val slot: FrameLayout) : RecyclerView.ViewHolder(slot)

    private var ad: NativeAd? = null
    private var showRow = true                       // LOADING (placeholder) or an ad
    private var attached: View? = null

    init {
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(viewModel.status, viewModel.ad) { status, ad -> status to ad }.collect { (status, newAd) ->
                    val wasShown = showRow
                    ad = newAd
                    showRow = newAd != null || status == NativeAdViewModel.Status.LOADING
                    when {
                        wasShown && !showRow -> notifyItemRemoved(0)
                        !wasShown && showRow -> notifyItemInserted(0)
                        showRow -> notifyItemChanged(0)
                    }
                }
            }
        }
    }

    /** Call on every visit: onResume() when not hidden, and onHiddenChanged(false). */
    fun onScreenVisible() = viewModel.refreshOnVisit(unitKey, isRowOnScreen())

    /** For a GridLayoutManager: the ad row spans the full width, every other item one column. */
    fun fullSpanLookup(spanCount: Int) = object : androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() {
        override fun getSpanSize(position: Int) = if (position < itemCount) spanCount else 1
    }

    override fun getItemCount() = if (showRow) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(FrameLayout(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                val px = (marginDp * parent.resources.displayMetrics.density).toInt()
                setMargins(px, px, px, px)
            }
        })

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val current = ad
        if (current != null) NativeAds.show(holder.slot, current, unitKey, size)
        else NativeAds.showPlaceholder(holder.slot, NativeAds.sizeFor(unitKey, size))
    }

    override fun onViewAttachedToWindow(holder: Holder) {
        attached = holder.itemView
        viewModel.onRowVisible(unitKey)                 // runs this visit's pending refresh, if any
    }

    override fun onViewDetachedFromWindow(holder: Holder) {
        if (attached === holder.itemView) attached = null
    }

    private fun isRowOnScreen(): Boolean = attached?.let { NativeAds.isOnScreen(it) } ?: false
}
