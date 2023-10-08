package de.lemke.oneurl.domain.model

enum class ShortUrlProvider {
    VGD,
    ISGD,
    DAGD,

    //URLDAY,
    //CHILPIT,
    TINYURL,
    ;

    override fun toString(): String = when (this) {
        VGD -> "v.gd"
        ISGD -> "is.gd"
        DAGD -> "da.gd"
        //URLDAY -> "urlday.com"
        //CHILPIT -> "chilp.it"
        TINYURL -> "tinyurl.com"
    }

    /* Examples
    https://v.gd/create.php?format=json&url=www.example.com&shorturl=example
    https://is.gd/create.php?format=json&url=www.example.com&shorturl=example
    https://da.gd/?url=http://some_long_url&shorturl=slug

    https://tinyurl.com/api-create.php?url=https://example.com&alias=example // json body: https://api.tinyurl.com/create
     */

    val baseUrl: String
        get() = when (this) {
            VGD -> "https://v.gd/"
            ISGD -> "https://is.gd/"
            DAGD -> "https://da.gd/"
            //URLDAY -> "https://urlday.com/"
            //CHILPIT -> "http://chilp.it/"
            TINYURL -> "https://tinyurl.com/"
        }

    fun getCreateUrlApi(longUrl: String, alias: String? = null): String = when (this) {
        VGD -> "${baseUrl}create.php?format=json&url=" + longUrl + (if (alias.isNullOrBlank()) "" else "&shorturl=$alias")
        ISGD -> "${baseUrl}create.php?format=json&url=" + longUrl + (if (alias.isNullOrBlank()) "" else "&shorturl=$alias")
        DAGD -> "${baseUrl}shorten?url=" + longUrl + (if (alias.isNullOrBlank()) "" else "&shorturl=$alias")
        //URLDAY -> "${baseUrl}"
        //CHILPIT -> "${baseUrl}api.php?url=" + longUrl + (if (alias.isNullOrBlank()) "" else "&slug=$alias")
        TINYURL -> "${baseUrl}api-create.php?url=" + longUrl + (if (alias.isNullOrBlank()) "" else "&alias=$alias")
    }

    fun getCheckUrlApi(alias: String): String = when (this) {
        VGD -> "${baseUrl}x"
        ISGD -> "${baseUrl}x"
        DAGD -> "${baseUrl}coshorten/$alias"
        //URLDAY -> "${baseUrl}"
        //CHILPIT -> "${baseUrl}"
        TINYURL -> "${baseUrl}x"
    }

    companion object {
        private val default = VGD

        private fun fromStringOrNull(string: String?): ShortUrlProvider? = when (string) {
            "v.gd" -> VGD
            "is.gd" -> ISGD
            "da.gd" -> DAGD
            //"urlday.com" -> URLDAY
            "tinyurl.com" -> TINYURL
            else -> null
        }

        fun fromString(string: String): ShortUrlProvider = with(fromStringOrNull(string)) {
            if (this != null) return@with this
            else throw IllegalArgumentException("Unknown ShortUrlProvider: $string")
        }

        fun fromStringOrDefault(string: String?): ShortUrlProvider = fromStringOrNull(string) ?: default

    }
}