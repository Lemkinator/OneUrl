package de.lemke.oneurl.domain.generateURL


import android.content.Context
import android.util.Log
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.model.ShortURLProvider
import javax.inject.Inject


class GenerateTINYURLUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    operator fun invoke(
        provider: ShortURLProvider,
        longURL: String,
        alias: String?,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (message: String) -> Unit,
    ): StringRequest {
        val tag = "GenerateURLUseCase_TINYURL"
        val apiURL = provider.getCreateURLApi(longURL, alias)
        Log.d(tag, "start request: $apiURL")
        return StringRequest(
            Request.Method.POST,
            provider.getCreateURLApi(longURL, alias),
            { response ->
                Log.d(tag, "response: $response")
                if (response.startsWith("https://tinyurl.com/")) {
                    val shortURL = response.trim()
                    Log.d(tag, "shortURL: $shortURL")
                    successCallback(shortURL)
                } else {
                    Log.e(tag, "error, response does not start with https://tinyurl.com/, response: $response")
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
                    when (statusCode) {
                        422 -> {
                            Log.e(tag, "error (422): alias already exists")
                            errorCallback(context.getString(R.string.error_alias_already_exists))
                        }

                        400 -> {
                            if (alias.isNullOrBlank()) {
                                Log.e(tag, "error (400): invalid URL")
                                errorCallback(context.getString(R.string.error_invalid_url))
                            } else {
                                Log.e(tag, "error (400): invalid URL or alias")
                                errorCallback(context.getString(R.string.error_invalid_url_or_alias))
                            }
                        }

                        else -> errorCallback(error.message ?: (context.getString(R.string.error_unknown) + " ($statusCode)"))
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