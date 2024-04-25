package de.lemke.oneurl.domain


import android.content.Context
import android.util.Log
import com.android.volley.toolbox.StringRequest
import dagger.hilt.android.qualifiers.ActivityContext
import de.lemke.oneurl.domain.generateURL.RequestQueueSingleton
import javax.inject.Inject


class GetURLTitleUseCase @Inject constructor(
    @ActivityContext private val context: Context,
) {
    operator fun invoke(url: String, callback: (title: String) -> Unit = { }) {
        RequestQueueSingleton.getInstance(context).addToRequestQueue(
            StringRequest(
                url.withHttps(),
                { response ->
                    try {
                        val title = Regex("<title>(.*?)</title>").find(response)?.groupValues?.get(1)
                        Log.d("GetURLTitleUseCase", "title: $title")
                        callback(title ?: "")
                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback("")
                    }
                },
                { error ->
                    error.printStackTrace()
                    callback("")
                }
            )
        )
    }
}
