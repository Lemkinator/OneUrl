package de.lemke.oneurl.domain.generateURL


import android.content.Context
import android.util.Log
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.domain.model.URL
import dev.oneuiproject.oneui.qr.QREncoder
import java.time.ZonedDateTime
import javax.inject.Inject


class GenerateULVISUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    operator fun invoke(
        provider: ShortURLProvider,
        longURL: String,
        alias: String?,
        favorite: Boolean,
        description: String,
        successCallback: (url: URL) -> Unit,
        errorCallback: (message: String) -> Unit,
    ): JsonObjectRequest {
        val tag = "GenerateULVISUseCase"
        val apiURL = provider.getCreateURLApi(longURL, alias)
        Log.d(tag, "start request: $apiURL")
        return JsonObjectRequest(
            Request.Method.POST,
            apiURL,
            null,
            { response ->
                Log.d(tag, "response: $response")
                /*
                success: response: {"success":true,"data":{"id":"EAZe","url":"https:\/\/ulvis.net\/EAZe","full":"https:\/\/t.com"}}
                alias already exists: response: {"success":true,"data":{"status":"custom-taken"}}
                code 0:
                {"success":false,"error":{"code":0,"msg":"domain not allowed"}}
                code 1:
                {"success":false,"error":{"code":1,"msg":"invalid url"}}
                code 2:
                {"success":false,"error":{"code":2,"msg":"custom name must be less than 60 chars"}}     //should not happen, checked before
                 */
                if (!response.optBoolean("success")) {
                    Log.e(tag, "error: ${response.optJSONObject("error")}")
                    val error = response.optJSONObject("error")
                    if (error != null) {
                        val code = error.optInt("code")
                        val msg = error.optString("msg")
                        when (code) {
                            0 -> errorCallback(context.getString(R.string.error_domain_not_allowed))
                            1 -> errorCallback(context.getString(R.string.error_invalid_url))
                            else -> errorCallback("$msg ($code)")
                        }
                        return@JsonObjectRequest
                    }
                    errorCallback(context.getString(R.string.error_unknown))
                    return@JsonObjectRequest
                }
                val data = response.optJSONObject("data")
                if (data == null) {
                    Log.e(tag, "error, response does not contain data")
                    errorCallback(context.getString(R.string.error_unknown))
                    return@JsonObjectRequest
                }
                if (data.optString("status") == "custom-taken") {
                    Log.e(tag, "error, alias already exists")
                    errorCallback(context.getString(R.string.error_alias_already_exists))
                    return@JsonObjectRequest
                }
                if (!data.has("url")) {
                    Log.e(tag, "error, response does not contain url")
                    errorCallback(context.getString(R.string.error_unknown))
                    return@JsonObjectRequest
                }
                val shortURL = data.getString("url").trim()
                Log.d(tag, "shortURL: $shortURL")
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