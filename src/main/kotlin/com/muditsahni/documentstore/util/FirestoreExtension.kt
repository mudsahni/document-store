package com.muditsahni.documentstore.util

import com.google.api.core.ApiFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

suspend fun <T> ApiFuture<T>.await(): T = suspendCoroutine { continuation ->
    addListener(
        {
            try {
                continuation.resume(get())
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        },
        MoreExecutors.directExecutor()
    )
}