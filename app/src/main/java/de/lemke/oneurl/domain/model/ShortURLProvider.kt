package de.lemke.oneurl.domain.model

enum class ShortURLProvider {
    DAGD,
    VGD,
    ISGD,
    //URLDAY,
    //CHILPIT,
    TINYURL,
    ;

    override fun toString(): String = when (this) {
        DAGD -> "da.gd"
        VGD -> "v.gd"
        ISGD -> "is.gd"
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

    val baseURL: String
        get() = when (this) {
            DAGD -> "https://da.gd/"
            VGD -> "https://v.gd/"
            ISGD -> "https://is.gd/"
            //URLDAY -> "https://urlday.com/"
            //CHILPIT -> "http://chilp.it/"
            TINYURL -> "https://tinyurl.com/"
        }

    fun getCreateURLApi(longURL: String, alias: String? = null): String = when (this) {
        DAGD -> "${baseURL}shorten?url=" + longURL + (if (alias.isNullOrBlank()) "" else "&shorturl=$alias")
        VGD -> "${baseURL}create.php?format=json&url=" + longURL + (if (alias.isNullOrBlank()) "" else "&shorturl=$alias")
        ISGD -> "${baseURL}create.php?format=json&url=" + longURL + (if (alias.isNullOrBlank()) "" else "&shorturl=$alias")
        //URLDAY -> "${baseURL}"
        //CHILPIT -> "${baseURL}api.php?url=" + longURL + (if (alias.isNullOrBlank()) "" else "&slug=$alias")
        TINYURL -> "${baseURL}api-create.php?url=" + longURL + (if (alias.isNullOrBlank()) "" else "&alias=$alias")
    }

    fun getCheckURLApi(alias: String): String = when (this) {
        DAGD -> "${baseURL}coshorten/$alias"
        VGD -> "${baseURL}x"
        ISGD -> "${baseURL}x"
        //URLDAY -> "${baseURL}"
        //CHILPIT -> "${baseURL}"
        TINYURL -> "${baseURL}x"
    }

    companion object {
        private val default = DAGD

        private fun fromStringOrNull(string: String?): ShortURLProvider? = when (string) {
            "da.gd" -> DAGD
            "v.gd" -> VGD
            "is.gd" -> ISGD
            //"urlday.com" -> URLDAY
            "tinyurl.com" -> TINYURL
            else -> null
        }

        fun fromString(string: String): ShortURLProvider = with(fromStringOrNull(string)) {
            if (this != null) return@with this
            else throw IllegalArgumentException("Unknown ShortURLProvider: $string")
        }

        fun fromStringOrDefault(string: String?): ShortURLProvider = fromStringOrNull(string) ?: default

    }
}