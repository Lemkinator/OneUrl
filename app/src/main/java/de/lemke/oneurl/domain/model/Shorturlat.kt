package de.lemke.oneurl.domain.model

import android.content.Context
import android.util.Log
import com.android.volley.NoConnectionError
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import de.lemke.oneurl.domain.generateURL.RequestQueueSingleton

/*
https://shorturl.at/
https://shorturl.at/shortener.php
from-urlencoded: u=https://example.com

Short URLs that do not have at least one click per month are disabled

response (parse html):
...
<main>
<section id="content">
<h1>Your shortened URL</h1>
<p>Copy the short link and share it in messages, texts, posts, websites and other locations.</p>
</section>
<script type="dbd3739ab688f365a1233f6a-text/javascript">
  var clipboard = new Clipboard('.copy');
  </script>
<script type="dbd3739ab688f365a1233f6a-text/javascript">
      function toggle_visibility(id) {
         var e = document.getElementById(id);
         if(e.style.display == "none")
            e.style.display = "table";
         //else
            //e.style.display = "none";
      }
  </script>
<section id="urlbox">
<br><br>
<div id="formurl" class="mw450">
<input id="shortenurl" type="text" value="https://shorturl.at/R8dPc" onClick="if (!window.__cfRLUnblockHandlers) return false; this.select();" data-cf-modified-dbd3739ab688f365a1233f6a->
<div id="formbutton">
<input type="button" data-clipboard-target="#shortenurl" class="copy" value="Copy URL" onclick="if (!window.__cfRLUnblockHandlers) return false; toggle_visibility('balloon');" data-cf-modified-dbd3739ab688f365a1233f6a->
</div>
</div>
<div id="formurl" class="mw450dblock">
<div id="balloon" style="display: none;">URL Copied</div>
</div>
<div id="formurl" class="mw450dblock">
<p class="boxtextleft">
Long URL: <a href="https://example1.com" target="_blank">https://example1.com</a><br><br>
<a href="url-total-clicks.php?u=shorturl.at/R8dPc" class="colorbuttonsmall">Total of clicks of your short URL</a><br>
<a href="https://www.shorturl.at/" class="colorbuttonsmall">Shorten another URL</a><br><br>
<span class="textmedium">* Short URLs that do not have at least one click per month are disabled</span>
</p>
...

fail:
...
<main>
<section id="content">
<h1>An error occurred creating the short URL</h1>
<p>The URL has not been shortened, possible errors:</p>
<ul>
<li class="list">Check if the domain is correct</li>
<li class="list">Check if the site is online</li>
<li class="list">Check the address bars and punctuation</li>
<li class="list">The URL may be being used for spam</li>
<li class="list">The URL may have been blocked</li>
<li class="list">The URL may have been reported</li>
<li class="list">The URL was recently shortened</li>
<li class="list">The URL is not allowed</li>
<li class="list">You shortened many URLs in a short time</li>
...

clicks:
https://www.shorturl.at/url-total-clicks.php?u=shorturl.at/2ssVp

response:
...
<h1>Total URL Clicks</h1>
<p>The number of clicks from the shortened URL that redirected the user to the destination page.</p>
<div class="squareboxurl"><a href="https://shorturl.at/2ssVp" target="_blank">shorturl.at/2ssVp</a></div>
<br>
<div class="squarebox"><div class="squareboxtext">0</div></div>
<p>
 */
val shorturlat = Shorturlat()

class Shorturlat : ShortURLProvider {
    override val enabled = false // form-urlencoded?
    override val name = "shorturl.at"
    override val baseURL = "https://shorturl.at"
    override val apiURL = "$baseURL/shortener.php"
    override val privacyURL = "$baseURL/privacy-policy.php"
    override val termsURL = "$baseURL/terms-of-service.php"

    override fun getInfoContents(context: Context): List<ProviderInfo> = listOf(
        ProviderInfo(
            dev.oneuiproject.oneui.R.drawable.ic_oui_labs,
            context.getString(de.lemke.commonutils.R.string.experimental),
            context.getString(R.string.shorturlat_info)
        ),
        ProviderInfo(
            dev.oneuiproject.oneui.R.drawable.ic_oui_report,
            context.getString(R.string.analytics),
            context.getString(R.string.analytics_text)
        )
    )

    override fun getTipsCardTitleAndInfo(context: Context) = Pair(
        context.getString(de.lemke.commonutils.R.string.info),
        context.getString(R.string.shorturlat_info)
    )

    override fun getURLClickCount(context: Context, url: URL, callback: (clicks: Int?) -> Unit) {
        val tag = "GetURLVisitCount_$name"
        val requestURL = "https://www.shorturl.at/url-total-clicks.php?u=${url.shortURL}"
        Log.d(tag, "start request: $url")
        RequestQueueSingleton.getInstance(context).addToRequestQueue(
            StringRequest(
                Request.Method.GET,
                requestURL,
                { response ->
                    try {
                        Log.d(tag, "response: $response")
                        val visitCount = response.split("<div class=\"squareboxtext\">")[1].split("</div>")[0].toIntOrNull()
                        Log.d(tag, "visitCount: $visitCount")
                        callback(visitCount)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback(null)
                    }
                },
                { error ->
                    Log.e(tag, "error: $error")
                    callback(null)
                }
            )
        )
    }

    override fun getCreateRequest(
        context: Context,
        longURL: String,
        alias: String,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit
    ): StringRequest {
        val tag = "CreateRequest_$name"
        Log.d(tag, "start request: $apiURL {u=$longURL}")
        return object : StringRequest(
            Method.POST,
            apiURL,
            { response ->
                try {
                    Log.d(tag, "response: $response")
                    val shortURL = response.split("shortenurl\" type=\"text\" value=\"")[1].split("\" onClick")[0]
                    Log.d(tag, "shortURL: $shortURL")
                    successCallback(shortURL)
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorCallback(GenerateURLError.Unknown(context, 200))
                }
            },
            { error ->
                try {
                    Log.e(tag, "error: $error")
                    val networkResponse = error.networkResponse
                    val statusCode = networkResponse?.statusCode
                    val data = networkResponse?.data?.toString(Charsets.UTF_8)
                    Log.e(tag, "$statusCode: message: ${error.message} data: $data")
                    when {
                        error is NoConnectionError -> errorCallback(GenerateURLError.ServiceOffline(context))
                        statusCode == null -> errorCallback(GenerateURLError.Unknown(context))
                        else -> errorCallback(GenerateURLError.Unknown(context, statusCode))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorCallback(GenerateURLError.Unknown(context))
                }
            }
        ) {
            //form-urlencoded: u=https://example.com doesnt work
            override fun getBody() = "u=$longURL".toByteArray(Charsets.UTF_8)
            override fun getBodyContentType() = "application/x-www-form-urlencoded; charset=UTF-8"
        }
    }
}