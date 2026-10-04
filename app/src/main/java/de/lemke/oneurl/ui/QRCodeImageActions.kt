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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import de.lemke.commonutils.data.SaveLocation
import de.lemke.commonutils.ui.utils.BitmapSaveResult
import de.lemke.commonutils.ui.utils.copyToClipboard
import de.lemke.commonutils.ui.utils.createBitmapClip
import de.lemke.commonutils.ui.utils.createBitmapShareFile
import de.lemke.commonutils.ui.utils.exportBitmap
import de.lemke.commonutils.ui.utils.quickShareBitmap
import de.lemke.commonutils.ui.utils.saveBitmapToDirectory
import de.lemke.commonutils.ui.utils.saveBitmapToUri
import de.lemke.commonutils.ui.utils.shareBitmap
import de.lemke.commonutils.ui.utils.singleLaunchSuspending
import de.lemke.commonutils.ui.utils.toast
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val QR_CODE_FILE_NAME = "QRCode.png"
private const val QR_CODE_CLIP_LABEL = "QR Code"

/**
 * Saves [qrCode] as a PNG named after [title] to [saveLocation] once per tap; a location that needs the document
 * picker launches it through [picker], whose result goes to [writePickedQRCode].
 */
internal fun ComponentActivity.saveQRCode(
    qrCode: Bitmap,
    title: String,
    saveLocation: SaveLocation,
    ioDispatcher: CoroutineDispatcher,
    picker: ActivityResultLauncher<Intent>,
) {
    singleLaunchSuspending(
        work = { saveBitmapToDirectory(saveLocation, qrCode, title, ioDispatcher) },
        then = { finishQRCodeSave(it, title, picker) },
    )
}

/** Saves [qrCode] once per tap through the launch latch of this fragment's activity; see [ComponentActivity.saveQRCode]. */
internal fun Fragment.saveQRCode(
    qrCode: Bitmap,
    title: String,
    saveLocation: SaveLocation,
    ioDispatcher: CoroutineDispatcher,
    picker: ActivityResultLauncher<Intent>,
) {
    singleLaunchSuspending(
        work = { saveBitmapToDirectory(saveLocation, qrCode, title, ioDispatcher) },
        then = { requireContext().finishQRCodeSave(it, title, picker) },
    )
}

private fun Context.finishQRCodeSave(
    result: BitmapSaveResult.DirectoryResult,
    title: String,
    picker: ActivityResultLauncher<Intent>,
) {
    when (result) {
        is BitmapSaveResult.Finished -> toast(result)
        BitmapSaveResult.NeedsPicker -> exportBitmap(title, picker)
    }
}

/** Opens the share sheet for [qrCode] once per tap. */
internal fun ComponentActivity.shareQRCode(
    qrCode: Bitmap,
    ioDispatcher: CoroutineDispatcher,
) {
    singleLaunchSuspending(
        work = { createBitmapShareFile(qrCode, QR_CODE_FILE_NAME, ioDispatcher) },
        then = { shareBitmap(it) },
    )
}

/** Opens the share sheet for [qrCode] once per tap through the launch latch of this fragment's activity. */
internal fun Fragment.shareQRCode(
    qrCode: Bitmap,
    ioDispatcher: CoroutineDispatcher,
) {
    singleLaunchSuspending(
        work = { createBitmapShareFile(qrCode, QR_CODE_FILE_NAME, ioDispatcher) },
        then = { shareBitmap(it) },
    )
}

/** Shares [qrCode] through Samsung Quick Share, or the share sheet without it, once per tap. */
internal fun Fragment.quickShareQRCode(
    qrCode: Bitmap,
    ioDispatcher: CoroutineDispatcher,
) {
    singleLaunchSuspending(
        work = { createBitmapShareFile(qrCode, QR_CODE_FILE_NAME, ioDispatcher) },
        then = { quickShareBitmap(it) },
    )
}

/** Copies [qrCode] to the clipboard once per tap. */
internal fun ComponentActivity.copyQRCode(
    qrCode: Bitmap,
    ioDispatcher: CoroutineDispatcher,
) {
    singleLaunchSuspending(
        work = { createBitmapClip(qrCode, QR_CODE_CLIP_LABEL, QR_CODE_FILE_NAME, ioDispatcher) },
        then = { copyToClipboard(it) },
    )
}

/**
 * Writes [qrCode] to the document picked through [exportBitmap] and shows the outcome. Neither a recreation nor a
 * dismissed sheet cancels the write, the cleanup of a failed write, or the toast.
 */
internal fun LifecycleOwner.writePickedQRCode(
    context: Context,
    result: ActivityResult,
    qrCode: Bitmap?,
    ioDispatcher: CoroutineDispatcher,
) {
    val document = result.toDocumentPick()
    val appContext = context.applicationContext
    lifecycleScope.launch {
        withContext(NonCancellable) {
            val saveResult =
                when (document) {
                    DocumentPick.Canceled -> BitmapSaveResult.Canceled
                    DocumentPick.MissingUri -> BitmapSaveResult.WriteFailed
                    is DocumentPick.Created -> appContext.saveBitmapToUri(document.uri, qrCode, createdDocument = true, ioDispatcher)
                }
            when (saveResult) {
                is BitmapSaveResult.Finished -> appContext.toast(saveResult)
                BitmapSaveResult.Canceled -> Unit
            }
        }
    }
}

private sealed interface DocumentPick {
    data class Created(
        val uri: Uri,
    ) : DocumentPick

    data object MissingUri : DocumentPick

    data object Canceled : DocumentPick
}

private fun ActivityResult.toDocumentPick(): DocumentPick {
    val uri = data?.data
    return when {
        resultCode != Activity.RESULT_OK -> DocumentPick.Canceled
        uri == null -> DocumentPick.MissingUri
        else -> DocumentPick.Created(uri)
    }
}
