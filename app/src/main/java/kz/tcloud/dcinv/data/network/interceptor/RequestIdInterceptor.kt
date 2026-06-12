package kz.tcloud.dcinv.data.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.util.UUID
import javax.inject.Inject

/**
 * Adds a unique `X-Request-ID` to every request for end-to-end correlation in
 * backend logs / NetBox journal entries (Architecture Overview §7.3).
 */
class RequestIdInterceptor @Inject constructor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("X-Request-ID", UUID.randomUUID().toString())
            .build()
        return chain.proceed(request)
    }
}
