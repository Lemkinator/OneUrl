package de.lemke.oneurl.domain

fun addHttpsIfMissing(url: String): String = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
