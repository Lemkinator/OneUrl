package de.lemke.oneurl.domain.generateURL


import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.widget.Toast
import dagger.hilt.android.qualifiers.ActivityContext
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.model.ShortURLProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject


class GenerateURLUseCase @Inject constructor(
    @ActivityContext private val context: Context,
) {
    suspend operator fun invoke(
        provider: ShortURLProvider,
        longURL: String,
        alias: String,
        successCallback: (shortURL: String) -> Unit = { },
        errorCallback: (error: GenerateURLError) -> Unit = { },
    ) = withContext(Dispatchers.Default) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        if (capabilities == null || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
            !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        ) {
            errorCallback(GenerateURLError.NoInternet(context))
            return@withContext
        }
        RequestQueueSingleton.getInstance(context).addToRequestQueue(
            provider.getCreateRequest(context, longURL, alias, successCallback, errorCallback)
        )
    }
}

sealed class GenerateURLError(
    val title: String,
    val message: String,
    val action: ErrorAction? = null,
) {
    class Unknown(context: Context) : GenerateURLError(context.getString(R.string.error), context.getString(R.string.error_unknown))
    class Custom(context: Context, customMessage: String? = null, customTitle: String? = null) :
        GenerateURLError(
            (customTitle ?: context.getString(R.string.error)).ifBlank { context.getString(R.string.error) },
            (customMessage ?: context.getString(R.string.error_unknown)).ifBlank { context.getString(R.string.error_unknown) }
        )

    class NoInternet(context: Context) : GenerateURLError(
        context.getString(R.string.no_internet),
        context.getString(R.string.no_internet_text),
        ErrorAction(
            context.getString(R.string.settings)
        ) {
            try {
                context.startActivity(Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, context.getString(R.string.error), Toast.LENGTH_SHORT).show()
            }
        }
    )

    class ServiceTemporarilyUnavailable(context: Context, provider: ShortURLProvider) :
        GenerateURLError(
            context.getString(R.string.error_service_unavailable),
            context.getString(R.string.error_service_unavailable_text),
            ErrorAction(
                context.getString(R.string.website)
            ) {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(provider.baseURL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, context.getString(R.string.no_browser_app_installed), Toast.LENGTH_SHORT).show()
                }
            }
        )

    class DomainNotAllowed(context: Context) : GenerateURLError(
        context.getString(R.string.error_domain_not_allowed),
        context.getString(R.string.error_domain_not_allowed)
    )

    class InvalidURL(context: Context) : GenerateURLError(
        context.getString(R.string.error),
        context.getString(R.string.error_invalid_url)
    )

    class AliasAlreadyExists(context: Context) : GenerateURLError(
        context.getString(R.string.error),
        context.getString(R.string.error_alias_already_exists)
    )

    class RateLimitExceeded(context: Context) : GenerateURLError(
        context.getString(R.string.error),
        context.getString(R.string.error_rate_limit_exceeded)
    )

    class InternalServerError(context: Context) : GenerateURLError(
        context.getString(R.string.error),
        context.getString(R.string.error_internal_server_error)
    )

    class HumanVerificationRequired(context: Context, provider: ShortURLProvider) :
        GenerateURLError(
            context.getString(R.string.error_human_verification),
            context.getString(R.string.error_human_verification_text),
            ErrorAction(
                context.getString(R.string.error_verify)
            ) {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(provider.baseURL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, context.getString(R.string.no_browser_app_installed), Toast.LENGTH_SHORT).show()
                }
            }
        )

    class ErrorAction(
        val title: String,
        val action: () -> Unit,
    )

}
