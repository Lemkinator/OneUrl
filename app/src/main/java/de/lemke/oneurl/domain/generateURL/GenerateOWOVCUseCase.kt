package de.lemke.oneurl.domain.generateURL


import android.content.Context
import android.util.Log
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.model.ShortURLProvider
import org.json.JSONObject
import javax.inject.Inject


class GenerateOWOVCUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    operator fun invoke(
        provider: ShortURLProvider,
        longURL: String,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (message: String) -> Unit,
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
                    errorCallback(context.getString(R.string.error_unknown))
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
                    if (statusCode == 400 && message.contains("link must match pattern")) {
                        Log.e(tag, "error: $message ($statusCode)")
                        errorCallback(context.getString(R.string.error_invalid_url))
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