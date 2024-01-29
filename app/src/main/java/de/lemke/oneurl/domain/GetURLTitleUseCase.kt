package de.lemke.oneurl.domain


import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.android.volley.RequestQueue
import com.android.volley.toolbox.BasicNetwork
import com.android.volley.toolbox.DiskBasedCache
import com.android.volley.toolbox.HurlStack
import com.android.volley.toolbox.StringRequest
import dagger.hilt.android.qualifiers.ActivityContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import javax.inject.Inject


class GetURLTitleUseCase @Inject constructor(
    @ActivityContext private val context: Context,
) {
    suspend operator fun invoke(
        url: String,
        callback: (title: String) -> Unit = { },
    ) = withContext(Dispatchers.Default) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        if (capabilities == null || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
            !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        ) {
            callback("")
            return@withContext
        }
        val cookieManager = CookieManager()
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL)
        CookieHandler.setDefault(cookieManager)
        // Instantiate the cache
        val cache = DiskBasedCache(context.cacheDir, 1024 * 1024) // 1MB cap
        // Set up the network to use HttpURLConnection as the HTTP client.
        val network = BasicNetwork(HurlStack())
        // Instantiate the RequestQueue with the cache and network. Start the queue.
        try {
            RequestQueue(cache, network).apply {
                start()
                add(
                    StringRequest(
                        addHttpsIfMissing(url),
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
        } catch (e: Exception) {
            e.printStackTrace()
            callback("")
        }
    }
}
