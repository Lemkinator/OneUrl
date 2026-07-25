package de.lemke.oneurl.domain

import android.content.Context
import android.util.Log
import com.android.volley.toolbox.StringRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lemke.commonutils.withHttps
import de.lemke.oneurl.domain.generateURL.RequestQueueSingleton
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

class GetURLTitleUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend operator fun invoke(url: String): String? = suspendCancellableCoroutine { cont ->
        val req = StringRequest(
            url.withHttps(),
            { response ->
                if (!cont.isActive) return@StringRequest
                try {
                    val title = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                        .find(response)?.groupValues?.get(1)?.trim()
                    Log.d("GetURLTitleUseCase", "title: $title")
                    cont.resume(title)
                } catch (e: Exception) {
                    e.printStackTrace()
                    cont.resume(null)
                }
            },
            { error ->
                if (!cont.isActive) return@StringRequest
                error.printStackTrace()
                cont.resume(null)
            }
        )
        RequestQueueSingleton.getInstance(context).addToRequestQueue(req)
        cont.invokeOnCancellation { req.cancel() }
    }
}
