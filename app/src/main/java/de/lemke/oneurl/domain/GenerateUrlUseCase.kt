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
import java.io.UnsupportedEncodingException
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
        alreadyShortenedCallback: (url:Url) -> Unit = { },
    ) = withContext(Dispatchers.Default) {
        with(getUrl(provider, longUrl)) {
            if (this != null) {
                alreadyShortenedCallback(this)
                return@withContext
            }
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
        val apiUrl = provider.getApiUrl(longUrl, alias)
        Log.d("GenerateUrlUseCase", "apiurl: $apiUrl")
        requestQueue.add(
            when (provider) {
                ShortUrlProvider.VGD, ShortUrlProvider.ISGD -> {
                    val tag = "GenerateUrlUseCase_VGD_ISGD"
                    JsonObjectRequest(
                        Request.Method.GET,
                        provider.getApiUrl(longUrl, alias),
                        null,
                        { response ->
                            Log.d(tag, "response: $response")
                            if (response.has("errorcode")) {
                                /*
                                Error code 1 - there was a problem with the original long URL provided
                                Error code 2 - there was a problem with the short URL provided (for custom short URLs)
                                Error code 3 - our rate limit was exceeded (your app should wait before trying again)
                                Error code 4 - any other error (includes potential problems with our service such as a maintenance period)
                                 */
                                Log.e(tag, "errorcode: ${response.getString("errorcode")}, errormessage: ${response.optString("errormessage")}")
                                errorCallback(response.optString("errormessage") + " (${response.getString("errorcode")})")
                                return@JsonObjectRequest
                            }
                            if (!response.has("shorturl")) {
                                Log.e(tag, "error, response does not contain shorturl, response: $response")
                                errorCallback(context.getString(R.string.error_unknown))
                                return@JsonObjectRequest
                            }
                            val shortUrl = response.getString("shorturl")
                            Log.d(tag, "shortUrl: $shortUrl")
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
                            val statusCode = java.lang.String.valueOf(error.networkResponse.statusCode)
                            Log.e(tag, "statusCode: $statusCode")
                            if (error.networkResponse.data != null) {
                                try {
                                    val body = error.networkResponse.data.toString(charset("UTF-8"))
                                    Log.e(tag, "error: body: $body")
                                    errorCallback(error.message ?: (context.getString(R.string.error_unknown) + " ($statusCode)"))
                                } catch (e: UnsupportedEncodingException) {
                                    e.printStackTrace()
                                    Log.e(tag, "error: UnsupportedEncodingException: $e")
                                    errorCallback(error.message ?: (context.getString(R.string.error_unknown) + " ($statusCode)"))
                                }
                            } else {
                                Log.e(tag, "error.networkResponse.data == null")
                                errorCallback(error.message ?: (context.getString(R.string.error_unknown) + " ($statusCode)"))
                            }
                        })
                }

                ShortUrlProvider.DAGD -> {
                    val tag = "GenerateUrlUseCase_DAGD"
                    val request = object : StringRequest(
                        Method.GET,
                        provider.getApiUrl(longUrl, alias),
                        { response ->
                            if (response.contains("https://da.gd/")) {
                                val shortUrl: String = "https://da.gd/" + response.substringAfter("https://da.gd/").substringBefore("</a>")
                                Log.d(tag, "shortUrl: $shortUrl")
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
                                Log.e(tag, "error, response does not contain https://da.gd/, response: $response")
                                errorCallback(context.getString(R.string.error_unknown))
                            }
                        },
                        { error ->
                            val statusCode = java.lang.String.valueOf(error.networkResponse.statusCode)
                            Log.e(tag, "statusCode: $statusCode")
                            if (error.networkResponse.data != null) {
                                try {
                                    val body = error.networkResponse.data.toString(charset("UTF-8"))
                                    val h1Message = body.substringAfter("<h1>").substringBefore("</h1>")
                                    Log.e(tag, "error: h1Message: $h1Message")
                                    errorCallback(h1Message)
                                } catch (e: UnsupportedEncodingException) {
                                    e.printStackTrace()
                                    Log.e(tag, "error: UnsupportedEncodingException: $e")
                                    errorCallback(error.message ?: (context.getString(R.string.error_unknown) + " ($statusCode)"))
                                }
                            } else {
                                Log.e(tag, "error.networkResponse.data == null")
                                errorCallback(error.message ?: (context.getString(R.string.error_unknown) + " ($statusCode)"))
                            }
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