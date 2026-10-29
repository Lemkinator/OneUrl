/*
 * Copyright 2023-2026 Leonard Lemke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.lemke.oneurl.domain.model

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.Response
import de.lemke.oneurl.data.VolleyQueue
import java.util.Locale
import de.lemke.commonutils.R as commonutilsR

/*
https://github.com/ShareX/ShareX/tree/develop/ShareX.UploadersLib/URLShorteners
https://github.com/738/awesome-url-shortener
https://github.com/public-apis/public-apis?tab=readme-ov-file#url-shorteners

https://cleanuri.com/docs //no alias, sometimes redirects to suspicious sites???
https://vurl.com/developers/ //no alias, shows hint before redirecting
https://turl.ca/api.php?url=https://example.com 500 (Internal Server Error)
https://reduced.to/ (These links will automatically be deleted after 30 minutes. Open a free account to keep them longer.)

offline:
https://nl.cm
https://2.gp
https://turl.ca

shutting down:
https://gotiny.cc/ (https://github.com/robvanbakel/gotiny-api)
https://chilp.it/
https://clicky.me/

requires api key:
https://kutt.it (also: Anonymous link creation has been disabled temporarily. Please log in.)
https://t2mio.com/
https://linksplit.io/
https://cutt.ly/
https://urlbae.com/

human verification:
https://ulvis.net/
https://shorturl.73.nu/
https://sor.bz/
 */

object ShortURLProviderCompanion {
    private val provider: List<ShortURLProvider> =
        listOf(
            VgdIsgd.Isgd,
            VgdIsgd.Vgd,
            Dagd,
            Kurzelinks.Kurzelinksde,
            Kurzelinks.Ocn,
            Kurzelinks.T1p,
            Kurzelinks.Ogy,
            Lstu,
            Tinube,
            Ulvis,
            Tinyurl,
            Onesis,
            Gg,
            L4f,
            Oneptco,
            Tnyim,
            Shareaholic,
            Murl,
            Tly.Default,
            Tly.Ibitly,
            Twtrto,
            Tly.Jpegly,
            Tly.Rebrandly,
            Tly.Bitly,
            Shrtlnk,
            Shorturlat,
            Zwsim,
            Spoome.Default,
            Spoome.Emoji,
            Owovc.Owo,
            Owovc.Zws,
            Owovc.Sketchy,
            Owovc.Gay,
        )

    // Follows the locale at process start; a locale change applies on the next app restart only.
    @SuppressLint("ConstantLocale")
    val all = providersFor(Locale.getDefault())

    val enabled = all.filter { it.enabled }

    internal fun providersFor(locale: Locale): List<ShortURLProvider> =
        if (locale.language == "de") provider else provider.filter { it !is Kurzelinks }

    val default: ShortURLProvider = VgdIsgd.Isgd

    fun getIfEnabledOrDefault(shortURLProvider: ShortURLProvider?): ShortURLProvider =
        if (shortURLProvider in enabled) shortURLProvider ?: default else default

    private fun fromStringOrNull(name: String?): ShortURLProvider? = provider.find { it.name == name }

    fun fromString(name: String): ShortURLProvider = fromStringOrNull(name) ?: Unknown()

    fun fromStringOrDefault(name: String?): ShortURLProvider = getIfEnabledOrDefault(fromStringOrNull(name))
}

/** A provider that only names stored links; it cannot create new ones. */
abstract class UnavailableProvider : ShortURLProvider {
    final override val enabled = false

    final override fun getCreateRequest(
        queue: VolleyQueue,
        longURL: String,
        alias: String,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit,
    ): Request<*> {
        Log.e("UnavailableProvider", "Tried to generate short URL with unavailable provider $name")
        errorCallback(GenerateURLError.Unknown())
        return object : Request<Any>(Method.GET, "", Response.ErrorListener { }) {
            override fun deliverResponse(response: Any?) {
                // no-op
            }

            override fun parseNetworkResponse(response: NetworkResponse?) = null
        }
    }
}

class Unknown : UnavailableProvider() {
    override val name = "Unknown"
    override val baseURL = "https://www.leonard-lemke.com/apps/oneurl"
}

object Twtrto : UnavailableProvider() {
    override val name = "t.ly (twtr.to)"
    override val baseURL = "https://t.ly"
}

@Suppress("SameReturnValue")
interface ShortURLProvider {
    val enabled: Boolean
        get() = true
    val name: String
    val group: String
        get() = name
    val baseURL: String
    val apiURL: String
        get() = baseURL
    val infoURL: String
        get() = baseURL
    val privacyURL: String?
        get() = null
    val termsURL: String?
        get() = null
    val aliasConfig: AliasConfig?
        get() = null
    val clickCountPeriod: ClickCountPeriod
        get() = ClickCountPeriod.AllTime

    fun getAnalyticsURL(alias: String): String? = null

    fun getURLClickCount(
        queue: VolleyQueue,
        url: URL,
        callback: (clicks: Int?) -> Unit,
    ) = callback(null)

    fun sanitizeLongURL(url: String): String = url.trim()

    fun getCreateRequest(
        queue: VolleyQueue,
        longURL: String,
        alias: String,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit,
    ): Request<*>

    fun getInfoContents(context: Context): List<ProviderInfo> = emptyList()

    fun getInfoButtons(context: Context): List<ProviderInfo> =
        listOfNotNull(
            privacyURL?.let {
                ProviderInfo(
                    dev.oneuiproject.oneui.R.drawable.ic_oui_privacy,
                    context.getString(commonutilsR.string.commonutils_privacy_policy),
                    it,
                )
            },
            termsURL?.let {
                ProviderInfo(
                    dev.oneuiproject.oneui.R.drawable.ic_oui_memo_outline,
                    context.getString(commonutilsR.string.commonutils_tos),
                    it,
                )
            },
            ProviderInfo(
                dev.oneuiproject.oneui.R.drawable.ic_oui_info_outline,
                context.getString(commonutilsR.string.commonutils_more_information),
                infoURL,
            ),
        )

    fun getTipsCardTitleAndInfo(context: Context): Pair<String, String>? = null
}

class ProviderInfo(
    val icon: Int,
    val title: String,
    val linkOrDescription: String,
)

interface AliasConfig {
    val minAliasLength: Int
    val maxAliasLength: Int
    val allowedAliasCharacters: String

    fun isAliasValid(alias: String): Boolean

    fun aliasLength(alias: String): Int = alias.length

    companion object {
        const val NO_MAX_ALIAS_SPECIFIED = 100
    }
}
