package de.lemke.oneurl.data.database

import de.lemke.oneurl.domain.model.ShortUrlProvider
import de.lemke.oneurl.domain.model.Url

fun urlFromDb(urlDb: UrlDb): Url {
    return Url(
        shortUrl = urlDb.shortUrl,
        longUrl = urlDb.longUrl,
        shortUrlProvider = ShortUrlProvider.fromString(urlDb.shortUrlProvider),
        qr = urlDb.qr,
        favorite = urlDb.favorite,
        description = urlDb.description,
        added = urlDb.added,
    )
}

fun urlToDb(url: Url): UrlDb {
    return UrlDb(
        shortUrl = url.shortUrl,
        longUrl = url.longUrl,
        shortUrlProvider = url.shortUrlProvider.toString(),
        qr = url.qr,
        favorite = url.favorite,
        description = url.description,
        added = url.added,
    )
}