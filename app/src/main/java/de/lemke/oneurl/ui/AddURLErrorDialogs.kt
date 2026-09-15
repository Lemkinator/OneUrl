/*
 * Copyright 2023-2026 Leonard Lemke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.lemke.oneurl.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AlertDialog
import de.lemke.commonutils.ui.utils.openURL
import de.lemke.commonutils.ui.utils.toast
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import de.lemke.commonutils.R as commonutilsR

fun AlertDialog.Builder.configureFor(
    context: Context,
    error: GenerateURLError,
) {
    when (error) {
        GenerateURLError.NoInternet -> {
            configureNoInternet(context)
        }

        is GenerateURLError.BlacklistedURL -> {
            configureBlacklisted(context, error)
        }

        is GenerateURLError.ServiceTemporarilyUnavailable -> {
            configureServiceUnavailable(context, error)
        }

        is GenerateURLError.Custom -> {
            configureCustom(context, error)
        }

        is GenerateURLError.Unknown -> {
            configureUnknown(context, error)
        }

        else -> {
            setTitle(commonutilsR.string.commonutils_error)
            setMessage(simpleErrorMessageRes(error))
        }
    }
}

private fun simpleErrorMessageRes(error: GenerateURLError): Int =
    when (error) {
        GenerateURLError.AliasAlreadyExists -> R.string.error_alias_already_exists
        GenerateURLError.URLExistsWithDifferentAlias -> R.string.error_url_already_exists_with_different_alias
        GenerateURLError.InvalidURL -> R.string.error_invalid_url
        GenerateURLError.InvalidAlias -> R.string.error_invalid_alias
        GenerateURLError.InvalidURLOrAlias -> R.string.error_invalid_url_or_alias
        GenerateURLError.InternalServerError -> R.string.error_internal_server_error
        GenerateURLError.ServiceOffline -> R.string.error_service_offline
        GenerateURLError.RateLimitExceeded -> R.string.error_rate_limit_exceeded
        GenerateURLError.DomainNotAllowed -> R.string.error_domain_not_allowed
        else -> commonutilsR.string.commonutils_error_unknown
    }

private fun AlertDialog.Builder.configureNoInternet(context: Context) {
    setTitle(R.string.no_internet)
    setMessage(R.string.no_internet_text)
    setPositiveButton(commonutilsR.string.commonutils_settings) { _, _ ->
        try {
            context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            Log.e("AddURLErrorDialogs", "could not open wireless settings", e)
            context.toast(commonutilsR.string.commonutils_error)
        }
    }
}

private fun AlertDialog.Builder.configureBlacklisted(
    context: Context,
    error: GenerateURLError.BlacklistedURL,
) {
    setTitle(commonutilsR.string.commonutils_error)
    setMessage(error.message ?: context.getString(R.string.error_blacklisted_url))
    if (error.urlhausLink != null) setPositiveButton(R.string.url_safety_urlhaus) { _, _ -> context.openURL(error.urlhausLink) }
    if (error.virustotalLink != null) setNegativeButton(R.string.url_safety_virustotal) { _, _ -> context.openURL(error.virustotalLink) }
}

private fun AlertDialog.Builder.configureServiceUnavailable(
    context: Context,
    error: GenerateURLError.ServiceTemporarilyUnavailable,
) {
    setTitle(R.string.error_service_unavailable)
    setMessage(R.string.error_service_unavailable_text)
    setPositiveButton(commonutilsR.string.commonutils_more_information) { _, _ -> context.openURL(error.providerBaseURL) }
}

private fun AlertDialog.Builder.configureCustom(
    context: Context,
    error: GenerateURLError.Custom,
) {
    setTitle(error.customTitle ?: "${context.getString(commonutilsR.string.commonutils_error)} (${error.statusCode})")
    setMessage(error.customMessage)
}

private fun AlertDialog.Builder.configureUnknown(
    context: Context,
    error: GenerateURLError.Unknown,
) {
    setTitle(commonutilsR.string.commonutils_error)
    setMessage(
        if (error.statusCode != null) {
            context.getString(R.string.error_unknown_with_status_code, error.statusCode)
        } else {
            context.getString(commonutilsR.string.commonutils_error_unknown)
        },
    )
}
