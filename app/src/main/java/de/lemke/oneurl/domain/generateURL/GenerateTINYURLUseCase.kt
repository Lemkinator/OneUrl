package de.lemke.oneurl.domain.generateURL


import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.domain.model.URL
import dev.oneuiproject.oneui.qr.QREncoder
import java.time.ZonedDateTime
import javax.inject.Inject


class GenerateTINYURLUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    operator fun invoke(
        provider: ShortURLProvider,
        longURL: String,
        alias: String?,
        favorite: Boolean,
        description: String,
        successCallback: (url: URL) -> Unit,
        errorCallback: (message: String) -> Unit
    ): StringRequest {
        val tag = "GenerateURLUseCase_TINYURL"
        val apiURL = provider.getCreateURLApi(longURL, alias)
        Log.d(tag, "start request: $apiURL")
        return StringRequest(
            Request.Method.POST,
            provider.getCreateURLApi(longURL, alias),
            { response ->
                Log.d(tag, "response: $response")
                if (response.startsWith("https://tinyurl.com/")) {
                    successCallback(
                        URL(
                            shortURL = response.trim(),
                            longURL = longURL,
                            shortURLProvider = provider,
                            qr = QREncoder(context, response.trim())
                                .setIcon(R.drawable.ic_launcher_themed)
                                .generate(),
                            favorite = favorite,
                            description = description,
                            added = ZonedDateTime.now()
                        )
                    )
                } else {
                    Log.e(tag, "error, response does not start with https://tinyurl.com/, response: $response")
                    errorCallback(context.getString(R.string.error_unknown))
                }
            },
            { error ->
                val statusCode = error.networkResponse.statusCode
                Log.e(tag, "error ($statusCode): $error")
                when (statusCode) {
                    422 -> {
                        Log.e(tag, "error (422): alias already exists")
                        errorCallback(context.getString(R.string.error_alias_already_exists))
                    }

                    400 -> {
                        Log.e(tag, "error (400): invalid URL or alias")
                        if (alias.isNullOrBlank()) errorCallback(context.getString(R.string.error_invalid_url))
                        else errorCallback(context.getString(R.string.error_invalid_url_or_alias))
                    }

                    else -> {
                        errorCallback(error.message ?: (context.getString(R.string.error_unknown) + " ($statusCode)"))
                    }
                }
            }
        )
    }
}