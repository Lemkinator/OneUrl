package de.lemke.oneurl.domain

import java.net.URLEncoder


fun String.withHttps() = if (this.startsWith("http://") || this.startsWith("https://")) this else "https://$this"

fun String.withoutHttps() = this.removePrefix("https://").removePrefix("http://").removeSuffix("/")

fun String.urlEncodeAmpersand() = this.replace("&", "%26")

fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")