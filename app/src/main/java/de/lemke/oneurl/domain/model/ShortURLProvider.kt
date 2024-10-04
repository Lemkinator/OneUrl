package de.lemke.oneurl.domain.model

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.Response
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import java.util.Locale

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

class ShortURLProviderCompanion {
    companion object {
        private val provider: List<ShortURLProvider> = listOf(
            dagd,
            isgd,
            vgd,
            kurzelinksde,
            kurzelinksdeOcn,
            kurzelinksdeT1p,
            kurzelinksdeOgy,
            lstu,
            tinube,
            tinyurl,
            gg,
            l4f,
            oneptco,
            ulvis,
            tinyim,
            shareaholic,
            tly,
            tlyIbitly,
            tlyTwtrto, //disabled
            tlyJpegly,
            tlyRebrandly, //disabled
            tlyBitly, //disabled
            shrtlnk, //disabled
            shorturlat, //disabled
            zwsim,
            spoome,
            spoomeEmoji,
            owovzOwo,
            owovzZws,
            owovzSketchy,
            owovzGay,
        )

        /*
        provide kurzelinks.de for German users only
        Assigning Locale.getDefault() to a final static field (suspicious)
        intended behavior: if the user changes locale while the app is running,
        the app will not update the list of available providers until restart
         */
        @SuppressLint("ConstantLocale")
        val all = if (Locale.getDefault().language == "de") provider else provider.filter { it !is Kurzelinksde }

        val enabled = all.filter { it.enabled }

        val default: ShortURLProvider = enabled.first()

        private fun fromStringOrNull(name: String?): ShortURLProvider? = provider.find { it.name == name }

        fun fromString(name: String): ShortURLProvider = fromStringOrNull(name) ?: Unknown()

        fun fromStringOrDefault(name: String?): ShortURLProvider = fromStringOrNull(name) ?: default
    }
}

class Unknown : ShortURLProvider {
    override val enabled = false
    override val name = "Unknown"
    override val baseURL = "https://www.leonard-lemke.com/apps/oneurl"

    override fun getCreateRequest(
        context: Context,
        longURL: String,
        alias: String,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit
    ): Request<*> {
        Log.e("UnknownProvider", "Tried to generate short URL with unknown provider")
        errorCallback(GenerateURLError.Unknown(context))
        return object : Request<Any>(Method.GET, "", Response.ErrorListener { }) {
            override fun deliverResponse(response: Any?) {}
            override fun parseNetworkResponse(response: NetworkResponse?) = null
        }
    }
}

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

    fun getAnalyticsURL(alias: String): String? = null

    fun getURLClickCount(context: Context, url: URL, callback: (clicks: Int?) -> Unit) = callback(null)

    fun sanitizeLongURL(url: String): String = url.trim()

    fun getCreateRequest(
        context: Context,
        longURL: String,
        alias: String,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit
    ): Request<*>

    fun getInfoContents(context: Context): List<ProviderInfo> = emptyList()
    fun getInfoButtons(context: Context): List<ProviderInfo> = listOfNotNull(
        privacyURL?.let { ProviderInfo(dev.oneuiproject.oneui.R.drawable.ic_oui_privacy, context.getString(R.string.privacy_policy), it) },
        termsURL?.let { ProviderInfo(dev.oneuiproject.oneui.R.drawable.ic_oui_memo_outline, context.getString(R.string.tos), it) },
        ProviderInfo(dev.oneuiproject.oneui.R.drawable.ic_oui_info_outline, context.getString(R.string.more_information), infoURL)
    )

    fun getTipsCardTitleAndInfo(context: Context): Pair<String, String>? = null
}

class ProviderInfo(
    val icon: Int,
    val title: String,
    val linkOrDescription: String
)

interface AliasConfig {
    companion object {
        const val NO_MAX_ALIAS_SPECIFIED = 100
    }

    val minAliasLength: Int
    val maxAliasLength: Int
    val allowedAliasCharacters: String
    fun isAliasValid(alias: String): Boolean
}