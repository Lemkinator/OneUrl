package de.lemke.oneurl.domain.model

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.Response
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import java.util.Locale

/*
https://github.com/ShareX/ShareX/tree/develop/ShareX.UploadersLib/URLShorteners

https://cleanuri.com/docs //no alias, sometimes redirects to suspicious sites???
https://vurl.com/developers/ //no alias, shows hint before redirecting
https://turl.ca/api.php?url=https://example.com 500 (Internal Server Error)
offline: nl.cm, 2.gp, turl.ca
requires api key: kutt.it
https://github.com/robvanbakel/gotiny-api
CHILPIT -> "${baseURL}api.php?url=" + longURL + (if (alias.isNullOrBlank()) "" else "&slug=$alias")
 */

class ShortURLProviderCompanion {
    companion object {
        private val all: List<ShortURLProvider> = listOf(
            Dagd(),
            VgdIsgd.Vgd(),
            VgdIsgd.Isgd(),
            Tinyurl(),
            Kurzelinksde.Kurzelinks(),
            Kurzelinksde.Ocn(),
            Kurzelinksde.T1p(),
            Kurzelinksde.Ogy(),
            Ulvis(), //disabled
            Oneptco(),
            Shareaholic(),
            Zwsim(),
            Owovz.OwovzOwo(),
            Owovz.OwovzZws(),
            Owovz.OwovzSketchy(),
            Owovz.OwovzGay(),
        )

        /*
        provide kurzelinks.de for German users only
        Assigning Locale.getDefault() to a final static field (suspicious)
        intended behavior: if the user changes locale while the app is running, the app will not update the list of available providers
         */
        @SuppressLint("ConstantLocale")
        val availableWithDisabled = if (Locale.getDefault().language == "de") all else all.filter { it !is Kurzelinksde }

        val available = availableWithDisabled.filter { it.enabled }

        val infoList = availableWithDisabled.distinctBy(ShortURLProvider::group)

        val default: ShortURLProvider = all.first()

        private fun fromStringOrNull(name: String?): ShortURLProvider? = all.find { it.name == name }

        fun fromString(name: String): ShortURLProvider = fromStringOrNull(name) ?: Unknown()

        fun fromStringOrDefault(name: String?): ShortURLProvider = fromStringOrNull(name) ?: default
    }
}

class Unknown : ShortURLProvider {
    override val enabled = false
    override val name = "Unknown"
    override val group = name
    override val baseURL = ""
    override val apiURL = ""
    override val infoURL = "https://www.leonard-lemke.com/apps/oneurl"
    override val privacyURL = null
    override val termsURL = null
    override val aliasConfig = null
    override fun getAnalyticsURL(alias: String) = null

    override fun sanitizeLongURL(url: String) = url.trim()

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

    //Info
    override val infoIcons = emptyList<Int>()
    override fun getInfoContents(context: Context) = emptyList<ProviderInfo>()
    override fun getInfoButtons(context: Context) = emptyList<ProviderInfo>()
    override fun getTipsCardTitleAndInfo(context: Context) = null
}

interface ShortURLProvider {
    val enabled: Boolean
    val group: String
    val name: String
    val baseURL: String
    val apiURL: String
    val infoURL: String
    val privacyURL: String?
    val termsURL: String?
    val aliasConfig: AliasConfig?

    fun getAnalyticsURL(alias: String): String?

    fun sanitizeLongURL(url: String): String

    fun getCreateRequest(
        context: Context,
        longURL: String,
        alias: String,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit
    ): Request<*>

    //Info
    val infoIcons: List<Int>
    fun getInfoContents(context: Context): List<ProviderInfo>
    fun getInfoButtons(context: Context): List<ProviderInfo>
    fun getTipsCardTitleAndInfo(context: Context): Pair<String, String>?
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