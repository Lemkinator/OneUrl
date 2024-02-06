package de.lemke.oneurl.domain.model

import de.lemke.oneurl.domain.withHttps

/* docs
    https://da.gd/help
    https://v.gd/apishorteningreference.php
    https://is.gd/apishorteningreference.php
    https://tinyurl.com/app
    https://kurzelinks.de/ https://ogy.de/ https://t1p.de/ https://0cn.de/ -> pdf
    https://ulvis.net/developer.html -> added cloudflare :/ -> removed
    https://github.com/1pt-co/api
    https://cleanuri.com/docs //no alias
    https://github.com/robvanbakel/gotiny-api
    https://owo.vc/api.html
     */

/* Examples
    https://v.gd/create.php?format=json&url=www.example.com&shorturl=example
    https://is.gd/create.php?format=json&url=www.example.com&shorturl=example
    https://da.gd/shorten?url=http://some_long_url&shorturl=slug
    https://tinyurl.com/api-create.php?url=https://example.com&alias=example // json body: https://api.tinyurl.com/create
    https://kurzelinks.de/api?key=API_KEY&json=1&apiversion=22&url=example.com&servicedomain=kurzelinks.de&requesturl=example
    https://ulvis.net/api.php?url=https://example.com&custom=alias&private=1 or
    https://ulvis.net/API/write/get?url=https://example.com&custom=alias&private=1
    https://csclub.uwaterloo.ca/~phthakka/1pt-express/addurl?long=test.com&short=test
    https://www.shareaholic.com/v2/share/shorten_link?url=example.com
    https://www.shareaholic.com/v2/share/shorten_link?apikey=8943b7fd64cd8b1770ff5affa9a9437b&url=example.com/&service[name]=bitly //requires apikey, but can use key from docs???
    https://owo.vc/api/v2/link {"link": "https://example.com", "generator": "owo", "metadata": "OWOIFY"}
     */

enum class ShortURLProvider {
    UNKNOWN,
    DAGD,
    VGD,
    ISGD,
    TINYURL,
    KURZELINKS,
    OCN, //o -> 0
    T1P,
    OGY,
    ULVIS,
    ONEPTCO,
    SHAREAHOLIC,
    OWOVC,
    OWOVCZWS,
    OWOVCSKETCHY,
    OWOVCGAY,
    ;

    val position: Int get() = all.indexOf(this)

    override fun toString(): String = when (this) {
        UNKNOWN -> "Unknown"
        DAGD -> "da.gd"
        VGD -> "v.gd"
        ISGD -> "is.gd"
        TINYURL -> "tinyurl.com"
        KURZELINKS -> "kurzelinks.de"
        OCN -> "0cn.de"
        T1P -> "t1p.de"
        OGY -> "ogy.de"
        ULVIS -> "ulvis.net"
        ONEPTCO -> "1pt.co"
        SHAREAHOLIC -> "go.shr.lc"
        OWOVC -> "owo.vc"
        OWOVCZWS -> "owo.vc (zws)"
        OWOVCSKETCHY -> "owo.vc (sketchy)"
        OWOVCGAY -> "owo.vc (gay)"
    }

    val minAliasLength: Int
        get() = when (this) {
            UNKNOWN -> 0
            DAGD -> 0
            VGD -> 5
            ISGD -> 5
            TINYURL -> 5
            KURZELINKS, OCN, T1P, OGY -> 5
            ULVIS -> 0
            ONEPTCO -> 0
            SHAREAHOLIC -> 0
            OWOVC, OWOVCZWS, OWOVCSKETCHY, OWOVCGAY -> 0
        }

    private val noMaxAlias = 100

    val maxAliasLength: Int
        get() = when (this) {
            UNKNOWN -> noMaxAlias
            DAGD -> 10
            VGD -> 30
            ISGD -> 30
            TINYURL -> 30
            ULVIS -> 60
            KURZELINKS, OCN, T1P, OGY -> 100
            ONEPTCO -> noMaxAlias
            SHAREAHOLIC -> noMaxAlias
            OWOVC, OWOVCZWS, OWOVCSKETCHY, OWOVCGAY -> noMaxAlias
        }

    val baseURL: String
        get() = when (this) {
            UNKNOWN -> ""
            DAGD -> "https://da.gd/"
            VGD -> "https://v.gd/"
            ISGD -> "https://is.gd/"
            TINYURL -> "https://tinyurl.com/"
            KURZELINKS -> "https://kurzelinks.de/"
            OCN -> "https://0cn.de/"
            T1P -> "https://t1p.de/"
            OGY -> "https://ogy.de/"
            ULVIS -> "https://ulvis.net/"
            ONEPTCO -> "https://1pt.co/"
            SHAREAHOLIC -> "https://www.shareaholic.com/"
            OWOVC, OWOVCZWS, OWOVCSKETCHY, OWOVCGAY -> "https://owo.vc/"
        }

    fun getCreateURLApi(longURL: String, alias: String? = null): String = when (this) {
        UNKNOWN -> ""
        DAGD -> "${baseURL}shorten?url=" + longURL + (if (alias.isNullOrBlank()) "" else "&shorturl=$alias")
        VGD -> "${baseURL}create.php?format=json&url=" + longURL + (if (alias.isNullOrBlank()) "" else "&shorturl=$alias&logstats=1")
        ISGD -> "${baseURL}create.php?format=json&url=" + longURL + (if (alias.isNullOrBlank()) "" else "&shorturl=$alias&logstats=1")
        TINYURL -> "${baseURL}api-create.php?url=" + longURL + (if (alias.isNullOrBlank()) "" else "&alias=$alias")
        KURZELINKS, OCN, T1P, OGY -> "${baseURL}api"
        ULVIS -> "${baseURL}API/write/get?url=" + longURL + (if (alias.isNullOrBlank()) "" else "&custom=$alias&private=1")
        ONEPTCO -> "https://csclub.uwaterloo.ca/~phthakka/1pt-express/addurl?long=$longURL" + (if (alias.isNullOrBlank()) "" else "&short=$alias")
        SHAREAHOLIC -> "${baseURL}v2/share/shorten_link?url=" + longURL
        OWOVC, OWOVCZWS, OWOVCSKETCHY, OWOVCGAY -> "${baseURL}api/v2/link" //{"link": "https://example.com", "generator": "owo", "metadata": "OWOIFY"}
        //CHILPIT -> "${baseURL}api.php?url=" + longURL + (if (alias.isNullOrBlank()) "" else "&slug=$alias")
    }

    fun getCheckURLApi(alias: String): String = when (this) {
        UNKNOWN -> ""
        DAGD -> "${baseURL}coshorten/$alias"
        VGD -> ""
        ISGD -> ""
        TINYURL -> ""
        KURZELINKS, OCN, T1P, OGY -> ""
        ULVIS -> ""
        ONEPTCO -> ""
        SHAREAHOLIC -> ""
        OWOVC, OWOVCZWS, OWOVCSKETCHY, OWOVCGAY -> "${baseURL}api/v2/link/$alias"
    }

    fun getAnalyticsURL(alias: String): String? = when (this) {
        UNKNOWN -> null
        DAGD -> "${baseURL}stats/$alias"
        VGD -> "${baseURL}stats.php?url=$alias"
        ISGD -> "${baseURL}stats.php?url=$alias"
        TINYURL -> null //requires api token
        KURZELINKS, OCN, T1P, OGY -> null //no analytics
        ULVIS -> null
        ONEPTCO -> null
        SHAREAHOLIC -> null
        OWOVC, OWOVCZWS, OWOVCSKETCHY, OWOVCGAY -> null //TODO check when online again :D :/ "${baseURL}api/v2/link/$alias"
    }

    val aliasConfigurable: Boolean get() = this.allowedAliasCharacters.isNotBlank()

    val allowedAliasCharacters: String
        get() = when (this) {
            UNKNOWN -> ""
            DAGD, VGD, ISGD, TINYURL, ONEPTCO -> "a-z, A-Z, 0-9, _"
            KURZELINKS, OCN, T1P, OGY -> "a-z, A-Z, 0-9, -, _"
            ULVIS -> "a-z, A-Z, 0-9"
            SHAREAHOLIC -> ""
            OWOVC, OWOVCZWS, OWOVCSKETCHY, OWOVCGAY -> ""
        }

    fun isAliasValid(alias: String): Boolean = when (this) {
        UNKNOWN -> false
        DAGD, VGD, ISGD, TINYURL, ONEPTCO -> alias.matches(Regex("[a-zA-Z0-9_]+"))
        KURZELINKS, OCN, T1P, OGY -> alias.matches(Regex("[a-zA-Z0-9_-]+"))
        ULVIS -> alias.matches(Regex("[a-zA-Z0-9]+"))
        SHAREAHOLIC -> true
        OWOVC, OWOVCZWS, OWOVCSKETCHY, OWOVCGAY -> true
    }

    fun sanitizeURL(url: String): String = with(url) {
        //add https if missing and provider requires it
        when (this@ShortURLProvider) {
            UNKNOWN -> this
            VGD, ISGD, TINYURL, KURZELINKS, OCN, T1P, OGY, ONEPTCO, SHAREAHOLIC -> this
            DAGD, ULVIS, OWOVC, OWOVCZWS, OWOVCSKETCHY, OWOVCGAY -> this.withHttps()
        }.trim()
    }

    val infoURL: String
        get() = when (this) {
            UNKNOWN -> "https://www.leonard-lemke.com/apps/oneurl"
            DAGD, VGD, ISGD, TINYURL, KURZELINKS, OCN, T1P, OGY, ULVIS, ONEPTCO, SHAREAHOLIC, OWOVC, OWOVCZWS, OWOVCSKETCHY, OWOVCGAY -> baseURL
        }

    val privacyURL: String?
        get() = when (this) {
            UNKNOWN -> null
            DAGD -> null
            VGD -> "https://v.gd/privacy.php"
            ISGD -> "https://is.gd/privacy.php"
            TINYURL -> "https://tinyurl.com/app/privacy-policy"
            KURZELINKS -> "https://kurzelinks.de/datenschutzerklaerung"
            OCN -> "https://0cn.de/datenschutzerklaerung"
            T1P -> "https://t1p.de/datenschutzerklaerung"
            OGY -> "https://ogy.de/datenschutzerklaerung"
            ULVIS -> "https://ulvis.net/privacy.html"
            ONEPTCO -> null
            SHAREAHOLIC -> "https://www.shareaholic.com/privacy/"
            OWOVC, OWOVCZWS, OWOVCSKETCHY, OWOVCGAY -> null
        }

    val termsURL: String?
        get() = when (this) {
            UNKNOWN -> null
            DAGD -> null
            VGD -> "https://v.gd/terms.php"
            ISGD -> "https://is.gd/terms.php"
            TINYURL -> "https://tinyurl.com/app/terms"
            KURZELINKS -> "https://kurzelinks.de/nutzungsbedingungen"
            OCN -> "https://0cn.de/nutzungsbedingungen"
            T1P -> "https://t1p.de/nutzungsbedingungen"
            OGY -> "https://ogy.de/nutzungsbedingungen"
            ULVIS -> "https://ulvis.net/disclaimer.html"
            ONEPTCO -> null
            SHAREAHOLIC -> "https://www.shareaholic.com/terms/"
            OWOVC, OWOVCZWS, OWOVCSKETCHY, OWOVCGAY -> null
        }

    companion object {
        private val default = DAGD

        val all: List<ShortURLProvider> = listOf(
            DAGD,
            VGD,
            ISGD,
            TINYURL,
            KURZELINKS,
            OCN,
            T1P,
            OGY,
            //ULVIS,
            ONEPTCO,
            SHAREAHOLIC,
            OWOVC,
            OWOVCZWS,
            OWOVCSKETCHY,
            OWOVCGAY,
        )

        private fun fromStringOrNull(string: String?): ShortURLProvider? = when (string) {
            "da.gd" -> DAGD
            "v.gd" -> VGD
            "is.gd" -> ISGD
            "tinyurl.com" -> TINYURL
            "kurzelinks.de" -> KURZELINKS
            "0cn.de" -> OCN
            "t1p.de" -> T1P
            "ogy.de" -> OGY
            "ulvis.net" -> ULVIS
            "1pt.co" -> ONEPTCO
            "go.shr.lc" -> SHAREAHOLIC
            "owo.vc" -> OWOVC
            "owo.vc (zws)" -> OWOVCZWS
            "owo.vc (sketchy)" -> OWOVCSKETCHY
            "owo.vc (gay)" -> OWOVCGAY
            else -> null
        }

        fun fromString(string: String): ShortURLProvider = fromStringOrNull(string) ?: UNKNOWN

        fun fromStringOrDefault(string: String?): ShortURLProvider = fromStringOrNull(string) ?: default

    }
}