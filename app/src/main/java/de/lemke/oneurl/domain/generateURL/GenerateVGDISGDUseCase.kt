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


class GenerateVGDISGDUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    operator fun invoke(
        provider: ShortURLProvider,
        longURL: String,
        alias: String?,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit = { },
    ): JsonObjectRequest {
        val tag = "GenerateURLUseCase_VGD_ISGD"
        val apiURL = provider.getCreateURLApi(longURL, alias)
        Log.d(tag, "start request: $apiURL")
        return JsonObjectRequest(
            Request.Method.GET,
            apiURL,
            null,
            { response ->
                Log.d(tag, "response: $response")
                if (response.has("errorcode")) {
                    Log.e(tag, "errorcode: ${response.getString("errorcode")}")
                    Log.e(tag, "errormessage: ${response.optString("errormessage")}")
                    /*
                    Error code 1 - there was a problem with the original long URL provided
                    Please specify a URL to shorten.                                            //should not happen, checked before
                    Please enter a valid URL to shorten
                    Error code 2 - there was a problem with the short URL provided (for custom short URLs)
                    Short URLs must be at least 5 characters long                               //should not happen, checked before
                    Short URLs may only contain the characters a-z, 0-9 and underscore          //should not happen, checked before
                    The shortened URL you picked already exists, please choose another.
                    Error code 3 - our rate limit was exceeded (your app should wait before trying again)
                    Error code 4 - any other error (includes potential problems with our service such as a maintenance period)
                     */
                    when (response.getString("errorcode")) {
                        "1" -> errorCallback(GenerateURLError.InvalidURL(context))
                        "2" -> errorCallback(GenerateURLError.AliasAlreadyExists(context))
                        "3" -> errorCallback(GenerateURLError.RateLimitExceeded(context))
                        "4" -> errorCallback(GenerateURLError.ServiceTemporarilyUnavailable(context, provider))
                        else -> errorCallback(
                            GenerateURLError.Custom(
                                context,
                                response.optString("errormessage") + " (${response.getString("errorcode")})"
                            )
                        )
                    }
                    return@JsonObjectRequest
                }
                if (!response.has("shorturl")) {
                    Log.e(tag, "error, response does not contain shorturl, response: $response")
                    errorCallback(GenerateURLError.Unknown(context))
                    return@JsonObjectRequest
                }
                val shortURL = response.getString("shorturl").trim()
                Log.d(tag, "shortURL: $shortURL")
                successCallback(shortURL)
            },
            { error ->
                try {
                    Log.e(tag, "error: $error")
                    val networkResponse: NetworkResponse? = error.networkResponse
                    val statusCode = networkResponse?.statusCode
                    Log.e(tag, "statusCode: $statusCode")
                    if (error.message == "org.json.JSONException: Value Error of type java.lang.String cannot be converted to JSONObject") {
                        //https://v.gd/create.php?format=json&url=example.com?test&shorturl=test21 -> Error, database insert failed
                        Log.e(tag, "error.message == ${error.message} (probably error: database insert failed)")
                        errorCallback(GenerateURLError.Custom(context, context.getString(R.string.error_vgd_isgd)))
                        return@JsonObjectRequest
                    }
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
                                context, (error.message ?: context.getString(R.string.error_unknown)) + " ($statusCode)"
                            )
                        )
                        return@JsonObjectRequest
                    }
                    val message = data.toString(charset("UTF-8"))
                    Log.e(tag, "error: $message ($statusCode)")
                    errorCallback(GenerateURLError.Custom(context, "$message ($statusCode)"))
                } catch (e: Exception) {
                    Log.e(tag, "error: $e")
                    e.printStackTrace()
                    errorCallback(GenerateURLError.Unknown(context))
                }
            }
        )
    }
}