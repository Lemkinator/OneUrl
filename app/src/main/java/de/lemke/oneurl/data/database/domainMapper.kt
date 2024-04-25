package de.lemke.oneurl.data.database

import de.lemke.oneurl.domain.model.ShortURLProviderCompanion
import de.lemke.oneurl.domain.model.URL

fun urlFromDb(urlDb: URLDb): URL = URL(
    shortURL = urlDb.shortURL,
    longURL = urlDb.longURL,
    shortURLProvider = ShortURLProviderCompanion.fromString(urlDb.shortURLProvider),
    qr = urlDb.qr,
    favorite = urlDb.favorite,
    title = urlDb.title,
    description = urlDb.description,
    added = urlDb.added,
)

fun urlToDb(url: URL): URLDb = URLDb(
    shortURL = url.shortURL,
    longURL = url.longURL,
    shortURLProvider = url.shortURLProvider.name,
    qr = url.qr,
    favorite = url.favorite,
    title = url.title,
    description = url.description,
    added = url.added,
)