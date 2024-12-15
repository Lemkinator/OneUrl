package de.lemke.oneurl.domain.model

import android.content.Context
import android.util.Log
import com.android.volley.NetworkResponse
import com.android.volley.NoConnectionError
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.StringRequest
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.generateURL.GenerateURLError

/*
example:
https://shrtlnk.dev/api/v2/link requires api key
but:
https://shrtlnk.dev/?index=&_data=routes%2F_index url=example.com
responds:
HTTP/1.1 204 No Content
Cache-Control: public, max-age=0, must-revalidate
Date: Sun, 22 Sep 2024 12:20:34 GMT
Server: Vercel
Strict-Transport-Security: max-age=63072000
X-Remix-Redirect: /new-link-added?key=h1aja4
X-Remix-Status: 302
X-Vercel-Cache: MISS
X-Vercel-Id: iad1::iad1::dx52r-1727007634821-62b637657b9e

so short link is: https://shrtlnk.dev/h1aja4
but has 10 seconds countdown before redirecting (can be skipped)
and sometimes shows ads?

 */
val shrtlnk = Shrtlnk()

class Shrtlnk : ShortURLProvider {
    override val enabled = false
    override val name = "shrtlnk.dev"
    override val baseURL = "https://www.shrtlnk.dev"
    override val apiURL = "$baseURL?index=&_data=routes%2F_index"

    override fun getInfoContents(context: Context): List<ProviderInfo> = listOf(
        ProviderInfo(
            dev.oneuiproject.oneui.R.drawable.ic_oui_confirm_before_next_action,
            context.getString(R.string.redirect_hint),
            context.getString(R.string.redirect_hint_text)
        )
    )

    override fun getCreateRequest(
        context: Context,
        longURL: String,
        alias: String,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit
    ): StringRequest {
        val tag = "CreateRequest_$name"
        Log.d(tag, "start request: $apiURL {url=$longURL}")
        return object : StringRequest(
            Method.POST,
            apiURL,
            null, // get shortURL from response headers
            null,
        ) {
            override fun getParams() = mutableMapOf("url" to longURL)
            override fun parseNetworkResponse(response: NetworkResponse?): Response<String> {
                try {
                    val headers = response?.headers
                    Log.d(tag, "response headers: $headers")
                    val redirect = headers?.get("X-Remix-Redirect")
                    val key = redirect?.substringAfter("key=")
                    if (key != null) {
                        val shortURL = "$baseURL/$key"
                        Log.d(tag, "shortURL: $shortURL")
                        successCallback(shortURL)
                        return Response.success(shortURL, null)
                    } else {
                        Log.e(tag, "error: redirect key not found")
                        errorCallback(GenerateURLError.Unknown(context))
                        return Response.error(VolleyError("redirect key not found"))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorCallback(GenerateURLError.Unknown(context))
                    return Response.error(VolleyError(e))
                }
            }

            override fun parseNetworkError(volleyError: VolleyError?): VolleyError {
                if (volleyError is NoConnectionError) {
                    errorCallback(GenerateURLError.ServiceOffline(context))
                } else {
                    errorCallback(GenerateURLError.Unknown(context))
                }
                return volleyError ?: VolleyError("unknown error")
            }
        }
    }
}