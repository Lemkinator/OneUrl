package de.lemke.oneurl.domain

import android.content.Context
import android.util.Log
import com.android.volley.toolbox.StringRequest
import dagger.hilt.android.qualifiers.ActivityContext
import de.lemke.oneurl.R
import org.json.JSONObject
import javax.inject.Inject

/*
https://www.phishtank.com/ restricted

https://urlhaus.abuse.ch/api/#automation
https://urlhaus-api.abuse.ch/
https://urlhaus-api.abuse.ch/v1/url/
url = https://raw.githubusercontent.com/hackirby/discord-injection/main/injection.js


query_status	The status of the query. Possibile values are:
ok	All good!
http_post_expected	The HTTP request was not HTTP POST
no_results	The query yield no results
invalid_host	Invalid host provided


blacklists	Blacklist status of the queried hostname (not available if host is an IPv4 address). The following blacklists are checked:

surbl	SURBL blacklist status. Possible values are:
listed	The queried malware URL is listed on SURBL
not listed	The queried malware URL is not listed on SURBL

spamhaus_dbl	Spamhaus DBL blacklist status. Possible values are:
spammer_domain	The queried malware URL is a known spammer domain
phishing_domain	The queried malware URL is a known phishing domain
botnet_cc_domain	The queried malware URL is a known botnet C&C domain
abused_legit_spam	The queried malware URL is a known compromised website used for spammer hosting
abused_legit_malware	The queried malware URL is a known compromised website used for malware distribution
abused_legit_phishing	The queried malware URL is a known compromised website used for phishing hosting
abused_legit_botnetcc	The queried malware URL is a known compromised website used for botnet C&C hosting
abused_redirector	The queried malware URL is a known abused redirector or URL shortener
not listed	The queried malware URL is not listed on Spamhaus DBL
 */

class UrlhausCheckUseCase @Inject constructor(
    @ActivityContext private val context: Context,
    ) {
    operator fun invoke(
        url: String,
        successCallback: () -> Unit,
        blacklistCallback: (message: String, urlhausLink: String?, virustotalLink: String?) -> Unit,
    ): StringRequest {
        val tag = "UrlhausCheckRequest"
        val checkUrlApi = "https://urlhaus-api.abuse.ch/v1/url"
        Log.d(tag, "start request: $checkUrlApi")
        return object : StringRequest(
            Method.POST,
            checkUrlApi,
            { response ->
                try {
                    Log.d(tag, "response: $response")
                    val responseJson = JSONObject(response)
                    val queryStatus = responseJson.optString("query_status")
                    if (queryStatus == "ok") {
                        val blacklists = responseJson.getJSONObject("blacklists")
                        val listedOn = "URLhaus" +
                                if (blacklists.getString("surbl") != "not listed") ", SURBL" else "" +
                                        if (blacklists.getString("spamhaus_dbl") != "not listed") ", Spamhaus" else ""
                        val reason = when (blacklists.optString("spamhaus_dbl")) {
                            "spammer_domain" -> context.getString(R.string.error_urlhaus_spammer_domain)
                            "phishing_domain" -> context.getString(R.string.error_urlhaus_phishing_domain)
                            "botnet_cc_domain" -> context.getString(R.string.error_urlhaus_botnet_cc_domain)
                            "abused_legit_spam" -> context.getString(R.string.error_urlhaus_abused_legit_spam)
                            "abused_legit_malware" -> context.getString(R.string.error_urlhaus_abused_legit_malware)
                            "abused_legit_phishing" -> context.getString(R.string.error_urlhaus_abused_legit_phishing)
                            "abused_legit_botnetcc" -> context.getString(R.string.error_urlhaus_abused_legit_botnetcc)
                            "abused_redirector" -> context.getString(R.string.error_urlhaus_abused_redirector)
                            else -> context.getString(R.string.error_urlhaus_default)
                        }
                        try {
                            val urlhausLink = responseJson.optString("urlhaus_reference").ifBlank { null }
                            val virustotalLink = responseJson.optJSONArray("payloads")?.optJSONObject(0)
                                ?.optJSONObject("virustotal")?.optString("link")?.ifBlank { null }
                            blacklistCallback(context.getString(R.string.error_urlhaus_blacklisted, listedOn, reason), urlhausLink, virustotalLink)
                        } catch (e: Exception) {
                            Log.w(tag, "Failed to get urlhaus_link or virustotal_link for $url: $e")
                            blacklistCallback(context.getString(R.string.error_urlhaus_blacklisted, listedOn, reason), null, null)
                        }
                    } else {
                        Log.d(tag, "Urlhaus Check returned no results for $url")
                        successCallback()
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Skipped Urlhaus Check for $url because of Exception: $e")
                    successCallback()
                }
            },
            { error ->
                Log.w(tag, "Skipped Urlhaus Check because of error: $error")
                successCallback()
            }
        ) {
            override fun getParams() = mapOf("url" to url)
        }
    }
}