package de.lemke.oneurl.domain


import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.VolleyError
import com.android.volley.toolbox.BasicNetwork
import com.android.volley.toolbox.DiskBasedCache
import com.android.volley.toolbox.HurlStack
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.model.ShortUrlProvider
import de.lemke.oneurl.domain.model.ShortUrlProvider.DAGD
import de.lemke.oneurl.domain.model.ShortUrlProvider.ISGD
import de.lemke.oneurl.domain.model.ShortUrlProvider.VGD
import de.lemke.oneurl.domain.model.ShortUrlProvider.TINYURL
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
        description: String,
        successCallback: (url: Url) -> Unit = { },
        errorCallback: (message: String) -> Unit = { },
        alreadyShortenedCallback: (url: Url) -> Unit = { },
    ) = withContext(Dispatchers.Default) {
        val existingURL = getUrl(provider, addHTTPSIfMissing(longUrl))
        if (existingURL.isNotEmpty()) {
            if (alias.isNullOrBlank()) {
                alreadyShortenedCallback(existingURL.first())
                return@withContext
            }
            for (url in existingURL) {
                if (url.shortUrl == provider.baseUrl + alias) {
                    alreadyShortenedCallback(url)
                    return@withContext
                }
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
        generate(provider, addHTTPSIfMissing(longUrl), alias, favorite, description, requestQueue, successCallback, errorCallback)
    }

    private fun addHTTPSIfMissing(url: String): String =
        if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"

    private fun generate(
        provider: ShortUrlProvider,
        longUrl: String,
        alias: String?,
        favorite: Boolean,
        description: String,
        requestQueue: RequestQueue,
        successCallback: (url: Url) -> Unit,
        errorCallback: (message: String) -> Unit
    ) {
        requestQueue.add(
            when (provider) {
                VGD, ISGD -> requestVGDOrISGD(provider, longUrl, alias, errorCallback, favorite, description, successCallback)
                DAGD -> requestDAGD(requestQueue, provider, longUrl, alias, favorite, description, successCallback, errorCallback)
                TINYURL -> requestTINYURL(provider, longUrl, alias, favorite, description, successCallback, errorCallback)
            }
        )
    }

    private fun requestVGDOrISGD(
        provider: ShortUrlProvider,
        longUrl: String,
        alias: String?,
        errorCallback: (message: String) -> Unit,
        favorite: Boolean,
        description: String,
        successCallback: (url: Url) -> Unit
    ): JsonObjectRequest {
        val tag = "GenerateUrlUseCase_VGD_ISGD"
        val apiUrl = provider.getCreateUrlApi(longUrl, alias)
        Log.d(tag, "start request: $apiUrl")
        return JsonObjectRequest(
            Request.Method.GET,
            apiUrl,
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
                    Log.e(tag, "errorcode: ${response.getString("errorcode")}")
                    Log.e(tag, "errormessage: ${response.optString("errormessage")}")
                    errorCallback(response.optString("errormessage") + " (${response.getString("errorcode")})")
                    return@JsonObjectRequest
                }
                if (!response.has("shorturl")) {
                    Log.e(tag, "error, response does not contain shorturl, response: $response")
                    errorCallback(context.getString(R.string.error_unknown))
                    return@JsonObjectRequest
                }
                val shortUrl = response.getString("shorturl").trim()
                Log.d(tag, "shortUrl: $shortUrl")
                successCallback(
                    Url(
                        shortUrl = shortUrl,
                        longUrl = longUrl,
                        shortUrlProvider = provider,
                        qr = QREncoder(context, shortUrl)
                            .setIcon(R.drawable.ic_launcher_themed)
                            .generate(),
                        favorite = favorite,
                        description = description,
                        added = ZonedDateTime.now()
                    )
                )
            },
            { error -> handlePlainTextError(VGD, tag, error, errorCallback) }
        )
    }

    private fun requestDAGD(
        requestQueue: RequestQueue,
        provider: ShortUrlProvider,
        longUrl: String,
        alias: String?,
        favorite: Boolean,
        description: String,
        successCallback: (url: Url) -> Unit,
        errorCallback: (message: String) -> Unit
    ): StringRequest {
        val tag = "GenerateUrlUseCase_DAGD"
        if (alias == null) return requestCreateDAGD(provider, longUrl, null, favorite, description, successCallback, errorCallback)
        val apiUrl = provider.getCheckUrlApi(alias)
        Log.d(tag, "start request: $apiUrl")
        return StringRequest(
            Request.Method.GET,
            provider.getCheckUrlApi(alias),
            { response ->
                if (response.trim() != longUrl) {
                    Log.e(tag, "error, shortUrl already exists, but has different longUrl, longUrl: $longUrl, response: $response")
                    errorCallback(context.getString(R.string.alias_already_exists))
                    return@StringRequest
                }
                Log.d(tag, "shortUrl already exists (but is not in local db): $response")
                val shortUrl = DAGD.baseUrl + alias
                successCallback(
                    Url(
                        shortUrl = shortUrl,
                        longUrl = longUrl,
                        shortUrlProvider = provider,
                        qr = QREncoder(context, shortUrl)
                            .setIcon(R.drawable.ic_launcher_themed)
                            .generate(),
                        favorite = favorite,
                        description = description,
                        added = ZonedDateTime.now()
                    )
                )
            },
            { error ->
                if (error.networkResponse.statusCode == 404) {
                    Log.d(tag, "shortUrl does not exist yet, creating it")
                    requestQueue.add(requestCreateDAGD(provider, longUrl, alias, favorite, description, successCallback, errorCallback))
                } else {
                    Log.w(tag, "error, statusCode: ${error.networkResponse.statusCode}, trying to create it anyway")
                    requestQueue.add(requestCreateDAGD(provider, longUrl, alias, favorite, description, successCallback, errorCallback))
                }
            }
        )
    }

    private fun requestCreateDAGD(
        provider: ShortUrlProvider,
        longUrl: String,
        alias: String?,
        favorite: Boolean,
        description: String,
        successCallback: (url: Url) -> Unit,
        errorCallback: (message: String) -> Unit
    ): StringRequest {
        val tag = "GenerateUrlUseCase_DAGD"
        val apiUrl = provider.getCreateUrlApi(longUrl, alias)
        Log.d(tag, "start request: $apiUrl")
        return StringRequest(
            Request.Method.GET,
            provider.getCreateUrlApi(longUrl, alias),
            { response ->
                Log.d(tag, "response: $response")
                if (response.startsWith("https://da.gd")) {
                    successCallback(
                        Url(
                            shortUrl = response.trim(),
                            longUrl = longUrl,
                            shortUrlProvider = provider,
                            qr = QREncoder(context, response.trim())
                                .setIcon(R.drawable.ic_launcher_themed)
                                .generate(),
                            favorite = favorite,
                            description = description,
                            added = ZonedDateTime.now()
                        )
                    )
                } else {
                    Log.e(tag, "error, response does not start with https://da.gd, response: $response")
                    errorCallback(context.getString(R.string.error_unknown))
                }
            },
            { error -> handlePlainTextError(DAGD, tag, error, errorCallback) }
        )
    }

    private fun requestTINYURL(
        provider: ShortUrlProvider,
        longUrl: String,
        alias: String?,
        favorite: Boolean,
        description: String,
        successCallback: (url: Url) -> Unit,
        errorCallback: (message: String) -> Unit
    ): StringRequest {
        val tag = "GenerateUrlUseCase_TINYURL"
        val apiUrl = provider.getCreateUrlApi(longUrl, alias)
        Log.d(tag, "start request: $apiUrl")
        return StringRequest(
            Request.Method.GET,
            provider.getCreateUrlApi(longUrl, alias),
            { response ->
                Log.d(tag, "response: $response")
                if (response.startsWith("https://tinyurl.com/")) {
                    successCallback(
                        Url(
                            shortUrl = response.trim(),
                            longUrl = longUrl,
                            shortUrlProvider = provider,
                            qr = QREncoder(context, response.trim())
                                .setIcon(R.drawable.ic_launcher_themed)
                                .generate(),
                            favorite = favorite,
                            description = description,
                            added = ZonedDateTime.now()
                        )
                    )
                } else {
                    Log.e(tag, "error, response does not start with https://tinyurl.com/, response: $response")
                    errorCallback(context.getString(R.string.error_unknown))
                }
            },
            { error -> handlePlainTextError(TINYURL, tag, error, errorCallback) }
        )
    }

    private fun handlePlainTextError(
        provider: ShortUrlProvider,
        tag: String,
        error: VolleyError,
        errorCallback: (message: String) -> Unit
    ) {
        val statusCode = error.networkResponse.statusCode
        Log.e(tag, "statusCode: $statusCode")
        if (provider == TINYURL && statusCode == 422) {
            Log.e(tag, "error: 422, probably already existing url or alias")
            errorCallback(context.getString(R.string.error_probably_existing_url_or_alias))
            return
        }
        if (error.networkResponse.data != null) {
            try {
                val message = error.networkResponse.data.toString(charset("UTF-8"))
                Log.e(tag, "error: $message ($statusCode)")
                errorCallback("$message ($statusCode)")
            } catch (e: UnsupportedEncodingException) {
                e.printStackTrace()
                Log.e(tag, "error: UnsupportedEncodingException: $e")
                errorCallback(error.message ?: (context.getString(R.string.error_unknown) + " ($statusCode)"))
            }
        } else {
            Log.e(tag, "error.networkResponse.data == null")
            errorCallback(error.message ?: (context.getString(R.string.error_unknown) + " ($statusCode)"))
        }
    }
}