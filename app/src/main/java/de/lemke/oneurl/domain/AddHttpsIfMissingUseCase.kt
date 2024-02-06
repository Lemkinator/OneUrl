package de.lemke.oneurl.domain

fun String.withHttps() = if (this.startsWith("http://") || this.startsWith("https://")) this else "https://$this"

fun String.withoutHttps() = this.removePrefix("https://").removePrefix("http://").removeSuffix("/")