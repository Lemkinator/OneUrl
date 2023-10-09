package de.lemke.oneurl.domain.model

enum class ShortURLProvider {
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

    val baseURL: String
        get() = when (this) {
            VGD -> "https://v.gd/"
            ISGD -> "https://is.gd/"
            DAGD -> "https://da.gd/"
            //URLDAY -> "https://urlday.com/"
            //CHILPIT -> "http://chilp.it/"
            TINYURL -> "https://tinyurl.com/"
        }

    fun getCreateURLApi(longURL: String, alias: String? = null): String = when (this) {
        VGD -> "${baseURL}create.php?format=json&url=" + longURL + (if (alias.isNullOrBlank()) "" else "&shorturl=$alias")
        ISGD -> "${baseURL}create.php?format=json&url=" + longURL + (if (alias.isNullOrBlank()) "" else "&shorturl=$alias")
        DAGD -> "${baseURL}shorten?url=" + longURL + (if (alias.isNullOrBlank()) "" else "&shorturl=$alias")
        //URLDAY -> "${baseURL}"
        //CHILPIT -> "${baseURL}api.php?url=" + longURL + (if (alias.isNullOrBlank()) "" else "&slug=$alias")
        TINYURL -> "${baseURL}api-create.php?url=" + longURL + (if (alias.isNullOrBlank()) "" else "&alias=$alias")
    }

    fun getCheckURLApi(alias: String): String = when (this) {
        VGD -> "${baseURL}x"
        ISGD -> "${baseURL}x"
        DAGD -> "${baseURL}coshorten/$alias"
        //URLDAY -> "${baseURL}"
        //CHILPIT -> "${baseURL}"
        TINYURL -> "${baseURL}x"
    }

    companion object {
        private val default = VGD

        private fun fromStringOrNull(string: String?): ShortURLProvider? = when (string) {
            "v.gd" -> VGD
            "is.gd" -> ISGD
            "da.gd" -> DAGD
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