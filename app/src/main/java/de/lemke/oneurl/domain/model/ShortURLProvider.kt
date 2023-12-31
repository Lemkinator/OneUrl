package de.lemke.oneurl.domain.model

import de.lemke.oneurl.domain.addHttpsIfMissing

/* docs
    https://v.gd/apishorteningreference.php /is.gd
    https://da.gd/help
    https://tinyurl.com/app
    https://ulvis.net/developer.html
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
    https://ulvis.net/api.php?url=https://example.com&custom=alias&private=1 or
    https://ulvis.net/API/write/get?url=https://example.com&custom=alias&private=1
    https://csclub.uwaterloo.ca/~phthakka/1pt-express/addurl?long=test.com&short=test
    https://owo.vc/api/v2/link {"link": "https://example.com", "generator": "owo", "metadata": "OWOIFY"}
     */

enum class ShortURLProvider {
    DAGD,
    VGD,
    ISGD,
    TINYURL,
    ULVIS,
    ONEPTCO,
    OWOVC,
    OWOVCZWS,
    OWOVCSKETCHY,
    OWOVCGAY,

    ;

    override fun toString(): String = when (this) {
        DAGD -> "da.gd"
        VGD -> "v.gd"
        ISGD -> "is.gd"
        TINYURL -> "tinyurl.com"
        ULVIS -> "ulvis.net"
        ONEPTCO -> "1pt.co"
        OWOVC -> "owo.vc"
        OWOVCZWS -> "owo.vc (zws)"
        OWOVCSKETCHY -> "owo.vc (sketchy)"
        OWOVCGAY -> "owo.vc (gay)"
    }

    val minAliasLength: Int
        get() = when (this) {
            DAGD -> 0
            VGD -> 5
            ISGD -> 5
            TINYURL -> 5
            ULVIS -> 0
            ONEPTCO -> 0
            OWOVC, OWOVCZWS, OWOVCSKETCHY, OWOVCGAY -> 0
        }

    private val noMaxAlias = 100

    val maxAliasLength: Int
        get() = when (this) {
            DAGD -> 10
            VGD -> 30
            ISGD -> 30
            TINYURL -> 30
            ULVIS -> 60
            ONEPTCO -> noMaxAlias
            OWOVC, OWOVCZWS, OWOVCSKETCHY, OWOVCGAY -> noMaxAlias
        }

    val baseURL: String
        get() = when (this) {
            DAGD -> "https://da.gd/"
            VGD -> "https://v.gd/"
            ISGD -> "https://is.gd/"
            TINYURL -> "https://tinyurl.com/"
            ULVIS -> "https://ulvis.net/"
            ONEPTCO -> "https://1pt.co/"
            OWOVC, OWOVCZWS, OWOVCSKETCHY, OWOVCGAY -> "https://owo.vc/"
        }

    private val apiURL: String
        get() = when (this) {
            DAGD -> "${baseURL}shorten"
            VGD -> "${baseURL}create.php"
            ISGD -> "${baseURL}create.php"
            TINYURL -> "${baseURL}api-create.php"
            //ULVIS -> "${baseURL}api.php"
            ULVIS -> "${baseURL}API/write/get"
            ONEPTCO -> "https://csclub.uwaterloo.ca/~phthakka/1pt-express/addurl"
            OWOVC, OWOVCZWS, OWOVCSKETCHY, OWOVCGAY -> "${baseURL}api/v2/link"
            //CHILPIT -> "${baseURL}api.php"
        }

    fun getCreateURLApi(longURL: String, alias: String? = null): String = when (this) {
        DAGD -> "${apiURL}?url=" + longURL + (if (alias.isNullOrBlank()) "" else "&shorturl=$alias")
        VGD -> "${apiURL}?format=json&url=" + longURL + (if (alias.isNullOrBlank()) "" else "&shorturl=$alias")
        ISGD -> "${apiURL}?format=json&url=" + longURL + (if (alias.isNullOrBlank()) "" else "&shorturl=$alias")
        TINYURL -> "${apiURL}?url=" + longURL + (if (alias.isNullOrBlank()) "" else "&alias=$alias")
        ULVIS -> "${apiURL}?url=" + longURL + (if (alias.isNullOrBlank()) "" else "&custom=$alias&private=1")
        ONEPTCO -> "${apiURL}?long=$longURL" + (if (alias.isNullOrBlank()) "" else "&short=$alias")
        OWOVC, OWOVCZWS, OWOVCSKETCHY, OWOVCGAY -> apiURL //{"link": "https://example.com", "generator": "owo", "metadata": "OWOIFY"}
        //CHILPIT -> "${apiURL}?url=" + longURL + (if (alias.isNullOrBlank()) "" else "&slug=$alias")
    }

    fun getCheckURLApi(alias: String): String = when (this) {
        DAGD -> "${baseURL}coshorten/$alias"
        VGD -> "${baseURL}todo"
        ISGD -> "${baseURL}todo"
        TINYURL -> "${baseURL}todo"
        ULVIS -> "${baseURL}todo"
        ONEPTCO -> "${baseURL}todo"
        OWOVC, OWOVCZWS, OWOVCSKETCHY, OWOVCGAY -> "${baseURL}api/v2/link/$alias"
    }

    val aliasConfigurable: Boolean get() = this.allowedAliasCharacters.isNotBlank()

    val allowedAliasCharacters: String
        get() = when (this) {
            DAGD, VGD, ISGD, TINYURL, ONEPTCO -> "a-z, A-Z, 0-9, _"
            ULVIS -> "a-z, A-Z, 0-9"
            OWOVC, OWOVCZWS, OWOVCSKETCHY, OWOVCGAY -> ""
        }

    fun isAliasValid(alias: String): Boolean = when (this) {
        DAGD, VGD, ISGD, TINYURL, ONEPTCO -> alias.matches(Regex("[a-zA-Z0-9_]+"))
        ULVIS -> alias.matches(Regex("[a-zA-Z0-9]+"))
        OWOVC, OWOVCZWS, OWOVCSKETCHY, OWOVCGAY -> true
    }

    fun sanitizeURL(url: String): String = with(url) {
        //add https if missing and provider requires it
        when (this@ShortURLProvider) {
            VGD, ISGD, TINYURL, ONEPTCO -> this
            DAGD, ULVIS, OWOVC, OWOVCZWS, OWOVCSKETCHY, OWOVCGAY -> addHttpsIfMissing(this)
        }.trim()
    }

    companion object {
        private val default = DAGD

        private fun fromStringOrNull(string: String?): ShortURLProvider? = when (string) {
            "da.gd" -> DAGD
            "v.gd" -> VGD
            "is.gd" -> ISGD
            "tinyurl.com" -> TINYURL
            "ulvis.net" -> ULVIS
            "1pt.co" -> ONEPTCO
            "owo.vc" -> OWOVC
            "owo.vc (zws)" -> OWOVCZWS
            "owo.vc (sketchy)" -> OWOVCSKETCHY
            "owo.vc (gay)" -> OWOVCGAY
            else -> null
        }

        @Throws(IllegalArgumentException::class)
        fun fromString(string: String): ShortURLProvider = with(fromStringOrNull(string)) {
            if (this != null) return@with this
            else throw IllegalArgumentException("Unknown ShortURLProvider: $string")
        }

        fun fromStringOrDefault(string: String?): ShortURLProvider = fromStringOrNull(string) ?: default

    }
}