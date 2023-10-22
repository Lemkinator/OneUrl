package de.lemke.oneurl.data.database

import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.domain.model.URL

fun urlFromDb(urlDb: URLDb): URL = URL(
    shortURL = urlDb.shortURL,
    longURL = urlDb.longURL,
    shortURLProvider = ShortURLProvider.fromString(urlDb.shortURLProvider),
    qr = urlDb.qr,
    favorite = urlDb.favorite,
    description = urlDb.description,
    added = urlDb.added,
)

fun urlToDb(url: URL): URLDb = URLDb(
    shortURL = url.shortURL,
    longURL = url.longURL,
    shortURLProvider = url.shortURLProvider.toString(),
    qr = url.qr,
    favorite = url.favorite,
    description = url.description,
    added = url.added,
)