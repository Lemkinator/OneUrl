package de.lemke.oneurl.domain.model

enum class ShortURLProvider {
    DAGD,
    VGD,
    ISGD,
    TINYURL,
    ULVIS,
    ;

    override fun toString(): String = when (this) {
        DAGD -> "da.gd"
        VGD -> "v.gd"
        ISGD -> "is.gd"
        TINYURL -> "tinyurl.com"
        ULVIS -> "ulvis.net"
    }

    /* Examples
    https://v.gd/create.php?format=json&url=www.example.com&shorturl=example
    https://is.gd/create.php?format=json&url=www.example.com&shorturl=example
    https://da.gd/shorten?url=http://some_long_url&shorturl=slug
    https://tinyurl.com/api-create.php?url=https://example.com&alias=example // json body: https://api.tinyurl.com/create
    https://ulvis.net/api.php?url=https://example.com&custom=alias&private=1 or
    https://ulvis.net/API/write/get?url=https://example.com&custom=alias&private=1
    urlday.com?
    chilp.it?
     */

    val minAliasLength: Int?
        get() = when (this) {
            DAGD -> null
            VGD -> 5
            ISGD -> 5
            TINYURL -> 5
            ULVIS -> null
        }

    val maxAliasLength: Int?
        get() = when (this) {
            DAGD -> 10
            VGD -> 30
            ISGD -> 30
            TINYURL -> 30
            ULVIS -> 60
        }

    val baseURL: String
        get() = when (this) {
            DAGD -> "https://da.gd/"
            VGD -> "https://v.gd/"
            ISGD -> "https://is.gd/"
            TINYURL -> "https://tinyurl.com/"
            ULVIS -> "https://ulvis.net/"
        }

    fun getCreateURLApi(longURL: String, alias: String? = null): String = when (this) {
        DAGD -> "${baseURL}shorten?url=" + longURL + (if (alias.isNullOrBlank()) "" else "&shorturl=$alias")
        VGD -> "${baseURL}create.php?format=json&url=" + longURL + (if (alias.isNullOrBlank()) "" else "&shorturl=$alias")
        ISGD -> "${baseURL}create.php?format=json&url=" + longURL + (if (alias.isNullOrBlank()) "" else "&shorturl=$alias")
        TINYURL -> "${baseURL}api-create.php?url=" + longURL + (if (alias.isNullOrBlank()) "" else "&alias=$alias")
        //ULVIS -> "${baseURL}api.php?url=" + longURL + (if (alias.isNullOrBlank()) "" else "&custom=$alias&private=1")
        ULVIS -> "${baseURL}API/write/get?url=" + longURL + (if (alias.isNullOrBlank()) "" else "&custom=$alias&private=1")
        //CHILPIT -> "${baseURL}api.php?url=" + longURL + (if (alias.isNullOrBlank()) "" else "&slug=$alias")
    }

    fun getCheckURLApi(alias: String): String = when (this) {
        DAGD -> "${baseURL}coshorten/$alias"
        VGD -> "${baseURL}x"
        ISGD -> "${baseURL}x"
        TINYURL -> "${baseURL}x"
        ULVIS -> "${baseURL}x"
    }

    val allowedAliasCharacters: String
        get() = when (this) {
            DAGD, VGD, ISGD, TINYURL -> "a-z, A-Z, 0-9, _"
            ULVIS -> "a-z, A-Z, 0-9"
        }

    fun isAliasValid(alias: String): Boolean = when (this) {
        DAGD, VGD, ISGD, TINYURL -> alias.matches(Regex("[a-zA-Z0-9_]+"))
        ULVIS -> alias.matches(Regex("[a-zA-Z0-9]+"))
    }

    companion object {
        private val default = DAGD

        private fun fromStringOrNull(string: String?): ShortURLProvider? = when (string) {
            "da.gd" -> DAGD
            "v.gd" -> VGD
            "is.gd" -> ISGD
            "tinyurl.com" -> TINYURL
            "ulvis.net" -> ULVIS
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