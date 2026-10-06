package com.movies.myapplication.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import com.movies.myapplication.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Loads TMDB images into ImageViews: an in-memory cache for instant re-display, OkHttp's disk cache
 * underneath, a short fade-in, and a couple of retries so a brief network drop doesn't leave a card blank.
 * Views in recycled list rows are tagged with the URL they should show, so a late download never lands
 * on a row that has moved on to another title.
 */
object ImageLoader {

    private const val RETRIES = 2

    private lateinit var client: OkHttpClient
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** An eighth of the app's memory, measured in KB. */
    private val memory = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    fun init(client: OkHttpClient) {
        this.client = client
    }

    /** Shows the image at [url] in [view]; a null URL clears it so the placeholder behind shows through. */
    fun load(view: ImageView, url: String?) {
        (view.getTag(R.id.image_job) as? Job)?.cancel()
        view.setTag(R.id.image_url, url)
        if (url == null) {
            view.setImageDrawable(null)
            return
        }
        memory.get(url)?.let {
            view.alpha = 1f
            view.setImageBitmap(it)
            return
        }
        view.setImageDrawable(null)
        val job = scope.launch {
            val bitmap = fetchWithRetries(url) ?: return@launch
            memory.put(url, bitmap)
            if (view.getTag(R.id.image_url) == url) {
                view.alpha = 0f
                view.setImageBitmap(bitmap)
                view.animate().alpha(1f).setDuration(200).start()
            }
        }
        view.setTag(R.id.image_job, job)
    }

    private suspend fun fetchWithRetries(url: String): Bitmap? {
        repeat(RETRIES + 1) { attempt ->
            fetch(url)?.let { return it }
            if (attempt < RETRIES) delay(1_000L * (attempt + 1))
        }
        return null
    }

    private suspend fun fetch(url: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (response.isSuccessful) response.body?.byteStream()?.let(BitmapFactory::decodeStream) else null
            }
        }.getOrNull()
    }
}
