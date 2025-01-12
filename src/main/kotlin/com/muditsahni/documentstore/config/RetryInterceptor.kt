package com.muditsahni.documentstore.config

import mu.KotlinLogging
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

// Custom retry interceptor
class RetryInterceptor(
    private val maxRetries: Int = 3,
    private val retryDelayMillis: Long = 1000
) : Interceptor {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        var retryCount = 0
        var response: Response? = null
        var exception: IOException? = null

        while (retryCount < maxRetries) {
            try {
                response = chain.proceed(chain.request())

                // Check if response was successful
                if (response.isSuccessful) {
                    return response
                } else if (!isRetryableStatus(response.code)) {
                    return response
                }

                response.close()

            } catch (e: IOException) {
                exception = e
                logger.warn("Attempt ${retryCount + 1} failed: ${e.message}")
            }

            retryCount++
            if (retryCount < maxRetries) {
                Thread.sleep(retryDelayMillis * retryCount)
            }
        }

        // If we got here, all retries failed
        throw exception ?: IOException("Request failed after $maxRetries attempts")
    }

    private fun isRetryableStatus(code: Int): Boolean {
        return when (code) {
            408, // Request Timeout
            429, // Too Many Requests
            500, // Internal Server Error
            502, // Bad Gateway
            503, // Service Unavailable
            504  // Gateway Timeout
            -> true
            else -> false
        }
    }
}
