package your.app.network

import okhttp3.Interceptor
import okhttp3.Response

class RateLimitInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var response = chain.proceed(chain.request())
        var attempt = 0
        while (response.code == 429 && attempt < 2) {
            val waitMs = response.header("Retry-After")?.toLongOrNull()?.times(1000) ?: (1000L shl attempt)
            response.close()
            Thread.sleep(waitMs.coerceAtMost(5_000L))
            attempt++
            response = chain.proceed(chain.request())
        }
        return response
    }
}
