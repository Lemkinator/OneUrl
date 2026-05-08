package de.lemke.oneurl.domain.generateURL

import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal suspend fun <T> RequestQueue.await(
    build: (Response.Listener<T>, Response.ErrorListener) -> Request<T>,
): T = suspendCancellableCoroutine { cont ->
    val req = build(
        { cont.resume(it) },
        { cont.resumeWithException(it) },
    )
    add(req)
    cont.invokeOnCancellation { req.cancel() }
}
