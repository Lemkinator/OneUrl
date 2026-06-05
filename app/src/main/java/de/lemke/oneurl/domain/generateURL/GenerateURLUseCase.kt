package de.lemke.oneurl.domain.generateURL

import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lemke.commonutils.withHttps
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.CheckURLSafetyUseCase
import de.lemke.oneurl.domain.model.ShortURLProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

class GenerateURLUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val checkURLSafety: CheckURLSafetyUseCase,
) {
    suspend operator fun invoke(
        provider: ShortURLProvider,
        longURL: String,
        alias: String,
        onProgress: (Int) -> Unit,
    ): GenerateURLResult {
        onProgress(R.string.checking_internet)
        val cm = context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        if (caps == null || !caps.hasCapability(NET_CAPABILITY_INTERNET) || !caps.hasCapability(NET_CAPABILITY_VALIDATED)) {
            return GenerateURLResult.Failure(GenerateURLError.NoInternet)
        }
        onProgress(R.string.checking_url)
        val normalizedURL = longURL.withHttps()
        val safetyResult = checkURLSafety(normalizedURL)
        if (safetyResult is CheckURLSafetyUseCase.UrlhausResult.Blacklisted) {
            return GenerateURLResult.Failure(
                GenerateURLError.BlacklistedURL(safetyResult.message, safetyResult.urlhausLink, safetyResult.virustotalLink)
            )
        }
        onProgress(R.string.generating_url)
        return provider.createShortURL(context, normalizedURL, alias)
    }
}

private suspend fun ShortURLProvider.createShortURL(context: Context, longURL: String, alias: String): GenerateURLResult =
    suspendCancellableCoroutine { cont ->
        val req = getCreateRequest(
            context, longURL, alias,
            successCallback = { shortURL ->
                if (cont.isActive) cont.resume(GenerateURLResult.Success(shortURL))
            },
            errorCallback = { error ->
                if (cont.isActive) cont.resume(GenerateURLResult.Failure(error))
            },
        )
        RequestQueueSingleton.getInstance(context).addToRequestQueue(req)
        cont.invokeOnCancellation { req.cancel() }
    }
