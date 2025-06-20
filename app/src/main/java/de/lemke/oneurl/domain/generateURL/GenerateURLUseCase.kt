package de.lemke.oneurl.domain.generateURL


import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import android.provider.Settings.ACTION_WIRELESS_SETTINGS
import dagger.hilt.android.qualifiers.ActivityContext
import de.lemke.commonutils.openURL
import de.lemke.commonutils.toast
import de.lemke.commonutils.withHttps
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.UrlhausCheckUseCase
import de.lemke.oneurl.domain.model.ShortURLProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import de.lemke.commonutils.R as commonutilsR


class GenerateURLUseCase @Inject constructor(
    @ActivityContext private val context: Context,
    private val urlhausCheck: UrlhausCheckUseCase,
) {
    suspend operator fun invoke(
        provider: ShortURLProvider,
        longURL: String,
        alias: String,
        setLoadingMessage: (Int) -> Unit,
        successCallback: (shortURL: String) -> Unit = { },
        errorCallback: (error: GenerateURLError) -> Unit = { },
    ) = withContext(Dispatchers.Default) {
        setLoadingMessage(R.string.checking_internet)
        val connectivityManager = context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        if (capabilities == null || !capabilities.hasCapability(NET_CAPABILITY_INTERNET) ||
            !capabilities.hasCapability(NET_CAPABILITY_VALIDATED)
        ) {
            errorCallback(GenerateURLError.NoInternet(context))
            return@withContext
        }
        setLoadingMessage(R.string.checking_url)
        RequestQueueSingleton.getInstance(context).addToRequestQueue(
            urlhausCheck(
                longURL.withHttps(),
                {
                    setLoadingMessage(R.string.generating_url)
                    RequestQueueSingleton.getInstance(context).addToRequestQueue(
                        provider.getCreateRequest(context, longURL, alias, successCallback, errorCallback)
                    )
                },
                { message, urlhausLink, virustotalLink ->
                    errorCallback(GenerateURLError.BlacklistedURL(context, message, urlhausLink, virustotalLink))
                }
            )
        )
    }
}

sealed class GenerateURLError(
    val title: String,
    val message: String,
    val actionOne: ErrorAction? = null,
    val actionTwo: ErrorAction? = null,
) {
    class Unknown(context: Context, statusCode: Int? = null) : GenerateURLError(
        context.getString(commonutilsR.string.commonutils_error) + if (statusCode != null) " ($statusCode)" else "",
        context.getString(commonutilsR.string.commonutils_error_unknown)
    )

    class Custom(context: Context, statusCode: Int, customMessage: String, customTitle: String? = null) :
        GenerateURLError(
            if (customTitle.isNullOrBlank()) context.getString(commonutilsR.string.commonutils_error) + " ($statusCode)" else customTitle,
            customMessage.ifBlank { context.getString(commonutilsR.string.commonutils_error_unknown) }
        )

    class NoInternet(context: Context) : GenerateURLError(
        context.getString(R.string.no_internet),
        context.getString(R.string.no_internet_text),
        ErrorAction(
            context.getString(commonutilsR.string.commonutils_settings)
        ) {
            try {
                context.startActivity(Intent(ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: ActivityNotFoundException) {
                context.toast(commonutilsR.string.commonutils_error)
            }
        }
    )

    class ServiceTemporarilyUnavailable(context: Context, provider: ShortURLProvider) :
        GenerateURLError(
            context.getString(R.string.error_service_unavailable),
            context.getString(R.string.error_service_unavailable_text),
            ErrorAction(
                context.getString(commonutilsR.string.commonutils_website)
            ) {
                context.openURL(provider.baseURL)
            }
        )

    class DomainNotAllowed(context: Context) : GenerateURLError(
        context.getString(R.string.error_domain_not_allowed),
        context.getString(R.string.error_domain_not_allowed)
    )

    class AliasAlreadyExists(context: Context) : GenerateURLError(
        context.getString(commonutilsR.string.commonutils_error),
        context.getString(R.string.error_alias_already_exists)
    )

    class URLExistsWithDifferentAlias(context: Context) : GenerateURLError(
        context.getString(commonutilsR.string.commonutils_error),
        context.getString(R.string.error_url_already_exists_with_different_alias)
    )

    class InvalidURL(context: Context) : GenerateURLError(
        context.getString(commonutilsR.string.commonutils_error),
        context.getString(R.string.error_invalid_url)
    )

    class InvalidAlias(context: Context) : GenerateURLError(
        context.getString(commonutilsR.string.commonutils_error),
        context.getString(R.string.error_invalid_alias)
    )

    class InvalidURLOrAlias(context: Context) : GenerateURLError(
        context.getString(commonutilsR.string.commonutils_error),
        context.getString(R.string.error_invalid_url_or_alias)
    )

    class BlacklistedURL(context: Context, message: String? = null, urlhausLink: String? = null, virustotalLink: String? = null) :
        GenerateURLError(
            context.getString(commonutilsR.string.commonutils_warning),
            message ?: context.getString(R.string.error_blacklisted_url),
            if (urlhausLink != null) ErrorAction("URLhaus") {
                context.openURL(urlhausLink)
            } else null,
            if (virustotalLink != null) ErrorAction("VirusTotal") {
                context.openURL(virustotalLink)
            } else null
        )

    class RateLimitExceeded(context: Context) : GenerateURLError(
        context.getString(commonutilsR.string.commonutils_error),
        context.getString(R.string.error_rate_limit_exceeded)
    )

    class InternalServerError(context: Context) : GenerateURLError(
        context.getString(commonutilsR.string.commonutils_error),
        context.getString(R.string.error_internal_server_error)
    )

    class ServiceOffline(context: Context) : GenerateURLError(
        context.getString(commonutilsR.string.commonutils_error),
        context.getString(R.string.error_service_offline)
    )

    class ErrorAction(
        val title: String,
        val action: () -> Unit,
    )

}
