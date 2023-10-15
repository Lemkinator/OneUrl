package de.lemke.oneurl.domain


import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.StringRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.domain.model.ShortURLProvider.DAGD
import de.lemke.oneurl.domain.model.URL
import dev.oneuiproject.oneui.qr.QREncoder
import java.io.UnsupportedEncodingException
import java.time.ZonedDateTime
import javax.inject.Inject


class GenerateDAGDUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    operator fun invoke(
        requestQueue: RequestQueue,
        provider: ShortURLProvider,
        longURL: String,
        alias: String?,
        favorite: Boolean,
        description: String,
        successCallback: (url: URL) -> Unit,
        errorCallback: (message: String) -> Unit
    ): StringRequest {
        val tag = "GenerateURLUseCase_DAGD"
        if (alias == null) return requestCreateDAGD(provider, longURL, null, favorite, description, successCallback, errorCallback)
        val apiURL = provider.getCheckURLApi(alias)
        Log.d(tag, "start request: $apiURL")
        return StringRequest(
            Request.Method.GET,
            provider.getCheckURLApi(alias),
            { response ->
                if (response.trim() != longURL) {
                    Log.e(tag, "error, shortURL already exists, but has different longURL, longURL: $longURL, response: $response")
                    errorCallback(context.getString(R.string.error_alias_already_exists))
                    return@StringRequest
                }
                Log.d(tag, "shortURL already exists (but is not in local db): $response")
                val shortURL = DAGD.baseURL + alias
                successCallback(
                    URL(
                        shortURL = shortURL,
                        longURL = longURL,
                        shortURLProvider = provider,
                        qr = QREncoder(context, shortURL)
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
                    Log.d(tag, "shortURL does not exist yet, creating it")
                    requestQueue.add(requestCreateDAGD(provider, longURL, alias, favorite, description, successCallback, errorCallback))
                } else {
                    Log.w(tag, "error, statusCode: ${error.networkResponse.statusCode}, trying to create it anyway")
                    requestQueue.add(requestCreateDAGD(provider, longURL, alias, favorite, description, successCallback, errorCallback))
                }
            }
        )
    }

    private fun requestCreateDAGD(
        provider: ShortURLProvider,
        longURL: String,
        alias: String?,
        favorite: Boolean,
        description: String,
        successCallback: (url: URL) -> Unit,
        errorCallback: (message: String) -> Unit
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
                    successCallback(
                        URL(
                            shortURL = response.trim(),
                            longURL = longURL,
                            shortURLProvider = provider,
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
            { error ->
                val statusCode = error.networkResponse.statusCode
                Log.e(tag, "statusCode: $statusCode")
                /* possible error messages:
                400: Long URL cannot be empty                           //should not happen, checked before
                400: Long URL must have http:// or https:// scheme.     //should not happen, checked before
                400: Long URL is not a valid URL.
                400: Short URL already taken. Pick a different one.
                400: Custom short URL contained invalid characters.     //should not happen, checked before
                 */
                if (error.networkResponse.data != null) {
                    try {
                        val message = error.networkResponse.data.toString(charset("UTF-8"))
                        Log.e(tag, "error: $message ($statusCode)")
                        when {
                            message.contains("Long URL cannot be empty") -> errorCallback(context.getString(R.string.error_invalid_url))
                            message.contains("Long URL must have http:// or https:// scheme") -> errorCallback(context.getString(R.string.error_invalid_url))
                            message.contains("Long URL is not a valid URL") -> errorCallback(context.getString(R.string.error_invalid_url))
                            message.contains("Short URL already taken") -> errorCallback(context.getString(R.string.error_alias_already_exists))
                            message.contains("Custom short URL contained invalid characters") -> errorCallback(context.getString(R.string.error_invalid_alias))
                            else -> errorCallback("$message ($statusCode)")
                        }
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
        )
    }
}