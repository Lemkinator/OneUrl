package de.lemke.oneurl.domain.generateURL


import android.content.Context
import android.util.Log
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.model.ShortURLProvider
import javax.inject.Inject


class GenerateONEPTCOUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    operator fun invoke(
        provider: ShortURLProvider,
        longURL: String,
        alias: String?,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (message: String) -> Unit,
    ): JsonObjectRequest {
        val tag = "GenerateONEPTCOUseCase"
        val apiURL = provider.getCreateURLApi(longURL, alias)
        Log.d(tag, "start request: $apiURL")
        return JsonObjectRequest(
            Request.Method.POST,
            apiURL,
            null,
            { response ->
                Log.d(tag, "response: $response")
                /*
                success: {
                    "message": "Added!",
                    "short": "ajodd",
                    "long": "t"
                }
                alias already exists: {
                    "message": "Added!",
                    "short": "asdfg",
                    "long": "test",
                    "receivedRequestedShort": false
                }
                fail: {
                    "message": "Bad request"
                }
                */
                if (!response.has("message")) {
                    Log.e(tag, "error: no message")
                    errorCallback(context.getString(R.string.error_unknown))
                    return@JsonObjectRequest
                }
                if (response.getString("message") != "Added!") {
                    Log.e(tag, "error: ${response.getString("message")}")
                    errorCallback(response.getString("message"))
                    return@JsonObjectRequest
                }
                if (!response.has("short")) {
                    Log.e(tag, "error: no short")
                    errorCallback(context.getString(R.string.error_unknown))
                    return@JsonObjectRequest
                }
                if (response.has("receivedRequestedShort") && !response.getBoolean("receivedRequestedShort")) {
                    Log.e(tag, "error: alias already exists")
                    errorCallback(context.getString(R.string.error_alias_already_exists))
                    return@JsonObjectRequest
                }
                val shortURL = provider.baseURL + response.getString("short").trim()
                Log.d(tag, "shortURL: $shortURL")
                successCallback(shortURL)
            },
            { error ->
                try {
                    Log.e(tag, "error: $error")
                    val networkResponse: NetworkResponse? = error.networkResponse
                    val statusCode = networkResponse?.statusCode
                    if (networkResponse == null || statusCode == null) {
                        Log.e(tag, "error.networkResponse == null")
                        errorCallback(error.message ?: context.getString(R.string.error_unknown))
                        return@JsonObjectRequest
                    }
                    Log.e(tag, "statusCode: $statusCode")
                    val data = networkResponse.data
                    if (data == null) {
                        Log.e(tag, "error.networkResponse.data == null")
                        errorCallback(error.message ?: (context.getString(R.string.error_unknown) + " ($statusCode)"))
                        return@JsonObjectRequest
                    }
                    val message = data.toString(charset("UTF-8"))
                    if (statusCode == 500 && message.contains("Internal server error")) {
                        Log.e(tag, "error: Internal server error ($statusCode)")
                        errorCallback(context.getString(R.string.error_unknown))
                        return@JsonObjectRequest
                    }
                    Log.e(tag, "error: $message ($statusCode)")
                    errorCallback("$message ($statusCode)")
                } catch (e: Exception) {
                    Log.e(tag, "error: $e")
                    e.printStackTrace()
                    errorCallback(error.message ?: context.getString(R.string.error_unknown))
                }
            }
        )
    }
}