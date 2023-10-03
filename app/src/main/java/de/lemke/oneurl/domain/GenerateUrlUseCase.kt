package de.lemke.oneurl.domain


import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.BasicNetwork
import com.android.volley.toolbox.DiskBasedCache
import com.android.volley.toolbox.HurlStack
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.model.ShortUrlProvider
import de.lemke.oneurl.domain.model.Url
import dev.oneuiproject.oneui.qr.QREncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.time.ZonedDateTime
import javax.inject.Inject

class GenerateUrlUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getUrl: GetUrlUseCase
) {
    suspend operator fun invoke(
        provider: ShortUrlProvider,
        longUrl: String,
        alias: String? = null,
        favorite: Boolean,
        successCallback: (url: Url) -> Unit = { },
        errorCallback: (message: String) -> Unit = { },
    ) = withContext(Dispatchers.Default) {
        if (getUrl(provider, longUrl) != null) {
            errorCallback(context.getString(R.string.url_already_exists))
            return@withContext
        }
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        if (capabilities == null || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
            !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        ) {
            errorCallback(context.getString(R.string.no_internet))
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
        val requestQueue = RequestQueue(cache, network).apply {
            start()
        }
        generate(provider, longUrl, alias, favorite, requestQueue, successCallback, errorCallback)
    }

    private fun generate(
        provider: ShortUrlProvider,
        longUrl: String,
        alias: String?,
        favorite: Boolean,
        requestQueue: RequestQueue,
        successCallback: (url: Url) -> Unit,
        errorCallback: (message: String) -> Unit
    ) {
        requestQueue.add(
            when (provider) {
                ShortUrlProvider.VGD, ShortUrlProvider.ISGD -> {
                    JsonObjectRequest(
                        Request.Method.GET,
                        provider.getApiUrl(longUrl, alias),
                        null,
                        { response ->
                            Log.d("GenerateUrlUseCase", "response: $response")
                            val shortUrl: String = if (response.has("errorcode") && response.getInt("errorcode") == 1) {
                                errorCallback(response.getString("errormessage"))
                                return@JsonObjectRequest
                            } else if (response.has("shorturl")) {
                                response.getString("shorturl")
                            } else {
                                errorCallback(context.getString(R.string.error_unknown))
                                return@JsonObjectRequest
                            }
                            val url = Url(
                                shortUrl = shortUrl,
                                longUrl = longUrl,
                                shortUrlProvider = provider,
                                qr = QREncoder(context, shortUrl)
                                    .setIcon(R.drawable.ic_launcher_themed)
                                    .generate(),
                                favorite = favorite,
                                added = ZonedDateTime.now()
                            )
                            successCallback(url)
                        },
                        { error ->
                            Log.e("GenerateUrlUseCase", "error: $error")
                            errorCallback(error.message ?: context.getString(R.string.error_unknown))
                        })
                }

                ShortUrlProvider.DAGD -> {
                    val request = object : StringRequest(
                        Method.GET,
                        provider.getApiUrl(longUrl, alias),
                        { response ->
                            Log.d("test", "apiurl: ${provider.getApiUrl(longUrl, alias)}")
                            Log.d("test", "response: $response")
                            if (response.contains("https://da.gd/")) {
                                val shortUrl: String = "https://da.gd/" + response.substringAfter("https://da.gd/").substringBefore("</a>")
                                val url = Url(
                                    shortUrl = shortUrl,
                                    longUrl = longUrl,
                                    shortUrlProvider = provider,
                                    qr = QREncoder(context, shortUrl)
                                        .setIcon(R.drawable.ic_launcher_themed)
                                        .generate(),
                                    favorite = favorite,
                                    added = ZonedDateTime.now()
                                )
                                successCallback(url)
                            } else {
                                errorCallback(context.getString(R.string.error_unknown))
                            }

                        },
                        { error ->
                            Log.e("GenerateUrlUseCase", "error: $error")
                            errorCallback(error.message ?: context.getString(R.string.error_unknown))
                        }) {
                        override fun getHeaders(): Map<String, String> = with(HashMap<String, String>()) {
                            this["User-Agent"] =
                                "Mozilla/5.0 (Linux; Android 6.0.1; Moto G (4)) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.45 Mobile Safari/537.36"
                            this["Accept"] = "text/html"
                            this
                        }
                    }
                    request
                }

                ShortUrlProvider.TINYURL -> TODO()
            }
        )
    }

}