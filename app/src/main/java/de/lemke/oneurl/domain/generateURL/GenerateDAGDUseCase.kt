package de.lemke.oneurl.domain.generateURL


import android.content.Context
import android.util.Log
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.StringRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.domain.model.ShortURLProvider.DAGD
import javax.inject.Inject


class GenerateDAGDUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    operator fun invoke(
        requestQueue: RequestQueue,
        provider: ShortURLProvider,
        longURL: String,
        alias: String?,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (message: String) -> Unit,
    ): StringRequest {
        val tag = "GenerateURLUseCase_DAGD"
        if (alias == null) return requestCreateDAGD(provider, longURL, null, successCallback, errorCallback)
        val apiURL = provider.getCheckURLApi(alias)
        Log.d(tag, "start request: $apiURL")
        return StringRequest(
            Request.Method.GET,
            apiURL,
            { response ->
                if (response.trim() != longURL) {
                    Log.e(tag, "error, shortURL already exists, but has different longURL, longURL: $longURL, response: $response")
                    errorCallback(context.getString(R.string.error_alias_already_exists))
                    return@StringRequest
                }
                Log.d(tag, "shortURL already exists (but is not in local db): $response")
                val shortURL = DAGD.baseURL + alias
                Log.d(tag, "shortURL: $shortURL")
                successCallback(shortURL)
            },
            { error ->
                try {
                    Log.w(tag, "error: $error")
                    val networkResponse: NetworkResponse? = error.networkResponse
                    val statusCode = networkResponse?.statusCode
                    if (networkResponse == null || statusCode == null) {
                        Log.e(tag, "error.networkResponse == null")
                        errorCallback(error.message ?: context.getString(R.string.error_unknown))
                        return@StringRequest
                    }
                    if (statusCode == 404) {
                        Log.d(tag, "shortURL does not exist yet, creating it")
                    } else {
                        Log.w(tag, "error, statusCode: ${error.networkResponse.statusCode}, trying to create it anyway")
                    }
                    requestQueue.add(requestCreateDAGD(provider, longURL, alias, successCallback, errorCallback))
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.e(tag, "error: $e")
                    errorCallback(context.getString(R.string.error_unknown))
                }
            }
        )
    }

    private fun requestCreateDAGD(
        provider: ShortURLProvider,
        longURL: String,
        alias: String?,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (message: String) -> Unit,
    ): StringRequest {
        val tag = "GenerateURLUseCase_DAGD"
        val apiURL = provider.getCreateURLApi(longURL, alias)
        Log.d(tag, "start request: $apiURL")
        return StringRequest(
            Request.Method.GET,
            provider.getCreateURLApi(longURL, alias),
            { response ->
                Log.d(tag, "response: $response")
                if (response.startsWith("https://da.gd")) {
                    val shortURL = response.trim()
                    Log.d(tag, "shortURL: $shortURL")
                    successCallback(shortURL)
                } else {
                    Log.e(tag, "error, response does not start with https://da.gd, response: $response")
                    errorCallback(context.getString(R.string.error_unknown))
                }
            },
            { error ->
                try {
                    Log.e(tag, "error: $error")
                    val networkResponse: NetworkResponse? = error.networkResponse
                    val statusCode = networkResponse?.statusCode
                    if (networkResponse == null || statusCode == null) {
                        Log.e(tag, "error.networkResponse == null")
                        errorCallback(error.message ?: context.getString(R.string.error_unknown))
                        return@StringRequest
                    }
                    Log.e(tag, "statusCode: $statusCode")
                    val data = networkResponse.data
                    if (data == null) {
                        Log.e(tag, "error.networkResponse.data == null")
                        errorCallback(error.message ?: (context.getString(R.string.error_unknown) + " ($statusCode)"))
                        return@StringRequest
                    }
                    val message = data.toString(charset("UTF-8"))
                    Log.e(tag, "error: $message ($statusCode)")
                    /* possible error messages:
                    400: Long URL cannot be empty                           //should not happen, checked before
                    400: Long URL must have http:// or https:// scheme.     //should not happen, checked before
                    400: Long URL is not a valid URL.
                    400: Short URL already taken. Pick a different one.
                    400: Custom short URL contained invalid characters.     //should not happen, checked before
                     */
                    when {
                        message.contains("Long URL cannot be empty") -> errorCallback(context.getString(R.string.error_invalid_url))
                        message.contains("Long URL must have http:// or https:// scheme") -> errorCallback(context.getString(R.string.error_invalid_url))
                        message.contains("Long URL is not a valid URL") -> errorCallback(context.getString(R.string.error_invalid_url))
                        message.contains("Short URL already taken") -> errorCallback(context.getString(R.string.error_alias_already_exists))
                        message.contains("Custom short URL contained invalid characters") -> errorCallback(context.getString(R.string.error_invalid_alias))
                        else -> errorCallback("$message ($statusCode)")
                    }
                } catch (e: Exception) {
                    Log.e(tag, "error: $e")
                    e.printStackTrace()
                    errorCallback(error.message ?: context.getString(R.string.error_unknown))
                }
            }
        )
    }
}