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
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import de.lemke.commonutils.data.SaveLocation
import de.lemke.commonutils.ui.utils.BitmapSaveResult
import de.lemke.commonutils.ui.utils.BitmapShareFile
import de.lemke.commonutils.ui.utils.copyToClipboard
import de.lemke.commonutils.ui.utils.exportBitmap
import de.lemke.commonutils.ui.utils.quickShareBitmap
import de.lemke.commonutils.ui.utils.shareBitmap
import de.lemke.commonutils.ui.utils.toast
import de.lemke.oneurl.data.QRCodeExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import de.lemke.commonutils.R as commonutilsR

/** A save, copy or share of a QR code. The screen acts on a [Result] and then reports it handled. */
sealed interface QRCodeExport {
    sealed interface Result : QRCodeExport

    data object Idle : QRCodeExport

    data object Running : QRCodeExport

    data class OpenPicker(val fileName: String) : Result

    data class SaveFinished(val result: BitmapSaveResult.Finished) : Result

    data class Copy(val clip: ClipData) : Result

    data object CopyFailed : Result

    data class Share(val file: BitmapShareFile.Written, val target: ShareTarget) : Result

    data object ShareFailed : Result
}

enum class ShareTarget {
    SHARE_SHEET,
    QUICK_SHARE,
}

sealed interface DocumentPick {
    data class Created(val uri: Uri) : DocumentPick

    data object MissingUri : DocumentPick

    data object Canceled : DocumentPick
}

/** Runs the QR code exports of a ViewModel in [scope], one at a time, and holds the state of the latest one. */
class QRCodeExportStateHolder(
    private val scope: CoroutineScope,
    private val exporter: QRCodeExporter,
) {
    val state: StateFlow<QRCodeExport>
        field = MutableStateFlow<QRCodeExport>(QRCodeExport.Idle)

    fun save(
        qrCode: Bitmap,
        fileName: String,
        location: SaveLocation,
    ) {
        start {
            when (val result = exporter.saveToDirectory(location, qrCode, fileName)) {
                is BitmapSaveResult.Finished -> QRCodeExport.SaveFinished(result)
                BitmapSaveResult.NeedsPicker -> QRCodeExport.OpenPicker(fileName)
            }
        }
    }

    /** Writes the QR code that [qrCode] returns, which may wait for a QR code still loading, into a created document. */
    fun onDocumentPicked(
        pick: DocumentPick,
        qrCode: suspend () -> Bitmap?,
    ) {
        when (pick) {
            DocumentPick.Canceled -> Unit
            DocumentPick.MissingUri -> state.value = QRCodeExport.SaveFinished(BitmapSaveResult.WriteFailed)
            is DocumentPick.Created -> launch { exporter.saveToCreatedDocument(pick.uri, qrCode()).toExport() }
        }
    }

    fun copy(qrCode: Bitmap) {
        start { exporter.createClip(qrCode)?.let(QRCodeExport::Copy) ?: QRCodeExport.CopyFailed }
    }

    fun share(
        qrCode: Bitmap,
        target: ShareTarget,
    ) {
        start {
            when (val file = exporter.createShareFile(qrCode)) {
                is BitmapShareFile.Written -> QRCodeExport.Share(file, target)
                BitmapShareFile.Failed -> QRCodeExport.ShareFailed
                BitmapShareFile.Dropped -> null
            }
        }
    }

    fun onHandled(result: QRCodeExport.Result) {
        state.update { if (it == result) QRCodeExport.Idle else it }
    }

    private fun start(work: suspend () -> QRCodeExport.Result?) {
        if (state.value == QRCodeExport.Running) return
        launch(work)
    }

    private fun launch(work: suspend () -> QRCodeExport.Result?) {
        state.value = QRCodeExport.Running
        scope.launch { state.value = work() ?: QRCodeExport.Idle }
    }

    private fun BitmapSaveResult.UriResult.toExport(): QRCodeExport.Result? =
        when (this) {
            is BitmapSaveResult.Finished -> QRCodeExport.SaveFinished(this)
            BitmapSaveResult.Canceled -> null
        }
}

internal fun ActivityResult.toDocumentPick(): DocumentPick {
    val uri = data?.data
    return when {
        resultCode != Activity.RESULT_OK -> DocumentPick.Canceled
        uri == null -> DocumentPick.MissingUri
        else -> DocumentPick.Created(uri)
    }
}

/**
 * Starts what [result] asks for: the document picker through [picker], a share, a copy or a toast.
 * @return true once [result] is handled; a share that starts nothing stays unhandled.
 */
internal fun Context.launchQRCodeExport(
    result: QRCodeExport.Result,
    picker: ActivityResultLauncher<Intent>,
): Boolean =
    when (result) {
        is QRCodeExport.OpenPicker -> {
            true.also { exportBitmap(result.fileName, picker) }
        }

        is QRCodeExport.Share -> {
            when (result.target) {
                ShareTarget.SHARE_SHEET -> shareBitmap(result.file)
                ShareTarget.QUICK_SHARE -> quickShareBitmap(result.file)
            }
        }

        is QRCodeExport.SaveFinished -> {
            true.also { toast(result.result) }
        }

        is QRCodeExport.Copy -> {
            true.also { copyToClipboard(result.clip) }
        }

        QRCodeExport.CopyFailed, QRCodeExport.ShareFailed -> {
            true.also { toast(commonutilsR.string.commonutils_error_share_content_not_supported_on_device) }
        }
    }
