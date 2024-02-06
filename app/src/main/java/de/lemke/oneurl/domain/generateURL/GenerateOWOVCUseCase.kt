package de.lemke.oneurl.domain.generateURL


import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.BasicNetwork
import com.android.volley.toolbox.DiskBasedCache
import com.android.volley.toolbox.HurlStack
import com.android.volley.toolbox.JsonObjectRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.domain.model.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import javax.inject.Inject


class GenerateOWOVCUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    operator fun invoke(
        provider: ShortURLProvider,
        longURL: String,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit = { },
    ): JsonObjectRequest {
        val generator = when (provider) {
            ShortURLProvider.OWOVCZWS -> "zws"
            ShortURLProvider.OWOVCSKETCHY -> "sketchy"
            ShortURLProvider.OWOVCGAY -> "gay"
            else -> "owo"
        }
        val tag = "GenerateOWOVCUseCase_$generator"
        val apiURL = provider.getCreateURLApi(longURL)
        Log.d(tag, "start request: $apiURL")
        return JsonObjectRequest(
            Request.Method.POST,
            apiURL,
            JSONObject(
                mapOf(
                    "link" to longURL,
                    "generator" to generator,
                    "metadata" to "IGNORE" //IGNORE, OWOIFY, PROXY
                )
            ),
            { response ->
                Log.d(tag, "response: $response")
                /*
                success:
                {
                    "id": "uvu.owo.vc/uwU-uvU.uwU-uwU",
                    "destination": "https://example.com",
                    "method": "OWO_VC",
                    "metadata": "OWOIFY",
                    "visits": 0,
                    "scrapes": 0,
                    "createdAt": "2023-10-24T20:41:21.597Z",
                    "status": "ACTIVE",
                    "commentId": null
                }
                fail:
                {
                    "statusCode": 400,
                    "code": "FST_ERR_VALIDATION",
                    "error": "Bad Request",
                    "message": "body/link must match pattern \"https?:\\/\\/.+\\..+\""
                }
                 */
                if (!response.has("id")) {
                    Log.e(tag, "error: no shortURL")
                    errorCallback(GenerateURLError.Unknown(context))
                    return@JsonObjectRequest
                }

                val shortURL = response.getString("id").trim()
                Log.d(tag, "shortURL: $shortURL")
                successCallback(shortURL)
            },
            { error ->
                try {
                    Log.e(tag, "error: $error")
                    val networkResponse: NetworkResponse? = error.networkResponse
                    val statusCode = networkResponse?.statusCode
                    Log.e(tag, "statusCode: $statusCode")
                    if (networkResponse == null || statusCode == null) {
                        Log.e(tag, "error.networkResponse == null")
                        errorCallback(GenerateURLError.Custom(context, error.message))
                        return@JsonObjectRequest
                    }
                    val data = networkResponse.data
                    if (data == null) {
                        Log.e(tag, "error.networkResponse.data == null")
                        errorCallback(
                            GenerateURLError.Custom(
                                context,
                                (error.message ?: context.getString(R.string.error_unknown)) + " ($statusCode)"
                            )
                        )
                        return@JsonObjectRequest
                    }
                    val message = data.toString(charset("UTF-8"))
                    Log.e(tag, "error: $message ($statusCode)")
                    if (statusCode == 400 && message.contains("link must match pattern")) {
                        errorCallback(GenerateURLError.InvalidURL(context))
                        return@JsonObjectRequest
                    } else if (statusCode == 503) {
                        errorCallback(GenerateURLError.ServiceTemporarilyUnavailable(context, provider))
                        return@JsonObjectRequest
                    }
                    errorCallback(GenerateURLError.Custom(context, "$message ($statusCode)"))
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorCallback(GenerateURLError.Unknown(context))
                }
            }
        )
    }

    suspend fun getURLVisitCount(url: URL, callback: (visitCount: Int?) -> Unit) = withContext(Dispatchers.Default) {
        val tag = "GetURLVisitCount_${url.shortURLProvider}"
        try {
            val apiURL = url.shortURLProvider.getAnalyticsURLApi() + Uri.encode(url.shortURL)
            Log.d(tag, "start request: $apiURL")
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            if (capabilities == null || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            ) {
                callback(null)
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
            RequestQueue(cache, network).apply {
                start()
                add(JsonObjectRequest(
                    Request.Method.GET,
                    apiURL,
                    null,
                    { response ->
                        Log.d(tag, "response: $response")
                        /*
                        {
                            "id": "uwu.owo.vc/uwU/uwU_Ovo/uvu",
                            "destination": "https://example.com",
                            "method": "OWO_VC",
                            "metadata": "OWOIFY",
                            "visits": 0,
                            "scrapes": 0,
                            "createdAt": "2024-02-06T16:11:00.509Z",
                            "status": "ACTIVE",
                            "commentId": null,
                            "comment": null
                        }
                        fail:
                        {
                            "statusCode": 404,
                            "error": "Not Found",
                            "message": "link not found"
                        }
                         */
                        val visitCount = response.optInt("visits")
                        Log.d(tag, "visitCount: $visitCount")
                        callback(visitCount)
                    },
                    { error ->
                        Log.e(tag, "error: $error")
                        callback(null)
                    }
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            callback(null)
        }
    }
}