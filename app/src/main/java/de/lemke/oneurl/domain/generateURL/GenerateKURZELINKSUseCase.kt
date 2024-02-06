package de.lemke.oneurl.domain.generateURL


import android.content.Context
import android.util.Log
import com.android.volley.NetworkResponse
import com.android.volley.toolbox.StringRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lemke.oneurl.BuildConfig
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.domain.withoutHttps
import org.json.JSONObject
import javax.inject.Inject


class GenerateKURZELINKSUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    operator fun invoke(
        provider: ShortURLProvider,
        longURL: String,
        alias: String?,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit = { },
    ): StringRequest {
        val tag = "GenerateURLUseCase_KURZELINKS"
        val apiURL = provider.getCreateURLApi(longURL, alias)
        Log.d(tag, "start request: $apiURL")
        val request = object : StringRequest(
            Method.POST,
            apiURL,
            { response ->
                Log.d(tag, "response: $response")
                try {
                    //response: {"@attributes":{"api":"22"},"status":"ok","shorturl":{"url":"https:\/\/kurzelinks.de\/014q","originalurl":"http:\/\/example.com"}}
                    val json = JSONObject(response)
                    val shortURL = json.getJSONObject("shorturl").getString("url")
                    Log.d(tag, "shortURL: $shortURL")
                    successCallback(shortURL)
                } catch (e: Exception) {
                    Log.e(tag, "error: $e")
                    e.printStackTrace()
                    errorCallback(GenerateURLError.Unknown(context))
                }
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
                    } else {
                        /*
                        400: Error occurred (please check the correctness of the request)
                        403: No permission for the operation is available (check API key)
                        423: the specified desired URL is not available
                        429: the maximum number of allowed requests has been exceeded
                        444: the API cannot be accessed at the moment (please try again later)
                         */
                        when (statusCode) {
                            400, 403 -> errorCallback(GenerateURLError.Unknown(context))
                            423 -> errorCallback(GenerateURLError.AliasAlreadyExists(context))
                            429 -> errorCallback(GenerateURLError.RateLimitExceeded(context))
                            444 -> errorCallback(GenerateURLError.ServiceTemporarilyUnavailable(context, provider))

                            else -> errorCallback(
                                GenerateURLError.Custom(
                                    context, (error.message ?: context.getString(R.string.error_unknown)) + " ($statusCode)"
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "error: $e")
                    e.printStackTrace()
                    errorCallback(GenerateURLError.Unknown(context))
                }
            }
        ) {
            override fun getParams(): MutableMap<String, String> = mutableMapOf(
                "key" to BuildConfig.KURZELINKS_API_KEY,
                "json" to "1",
                "apiversion" to "22",
                "url" to longURL,
                "servicedomain" to provider.baseURL.withoutHttps(),
                "requesturl" to (alias ?: "")
            )
        }
        return request
    }
}