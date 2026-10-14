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

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lemke.commonutils.ui.utils.BitmapExport
import de.lemke.commonutils.ui.utils.BitmapExportStateHolder
import de.lemke.commonutils.ui.utils.BitmapExporter
import de.lemke.commonutils.ui.utils.DocumentPick
import de.lemke.commonutils.ui.utils.ShareTarget
import de.lemke.oneurl.data.UserSettings
import de.lemke.oneurl.domain.GenerateQRCodeUseCase
import de.lemke.oneurl.domain.model.QRCodeStyle
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class GenerateQRCodeViewModel @Inject constructor(
    private val userSettings: UserSettings,
    private val generateQRCode: GenerateQRCodeUseCase,
    exporter: BitmapExporter,
) : ViewModel() {
    private val qrCodeExport = BitmapExportStateHolder(viewModelScope, exporter)
    val state: StateFlow<QrUiState>
        field = MutableStateFlow(initialState())
    val export: StateFlow<BitmapExport> = qrCodeExport.state
    private var urlSaveJob: Job? = null
    private var sizeSaveJob: Job? = null

    fun setUrl(url: String) {
        state.update { it.copy(url = url) }
        regenerate()
        urlSaveJob?.cancel()
        urlSaveJob =
            viewModelScope.launch {
                delay(300.milliseconds)
                userSettings.qrURL = url
            }
    }

    fun setSize(size: Int) {
        updateStyle { it.copy(size = size) }
        sizeSaveJob?.cancel()
        sizeSaveJob =
            viewModelScope.launch {
                delay(300.milliseconds)
                userSettings.qrSize = size
            }
    }

    fun setRoundedFrame(enabled: Boolean) {
        updateStyle { it.copy(roundedFrame = enabled) }
        userSettings.qrFrame = enabled
    }

    fun setIcon(enabled: Boolean) {
        updateStyle { it.copy(icon = enabled) }
        userSettings.qrIcon = enabled
    }

    fun setTintBorder(enabled: Boolean) {
        updateStyle { it.copy(tintBorder = enabled) }
        userSettings.qrTintBorder = enabled
    }

    fun setTintAnchor(enabled: Boolean) {
        updateStyle { it.copy(tintAnchor = enabled) }
        userSettings.qrTintAnchor = enabled
    }

    fun setForegroundColor(color: Int) {
        val recentColors =
            state.value.recentForegroundColors
                .toMutableList()
                .also { it.add(0, color) }
                .distinct()
                .take(6)
        state.update { it.copy(style = it.style.copy(foregroundColor = color), recentForegroundColors = recentColors) }
        userSettings.qrRecentForegroundColors = recentColors
        regenerate()
    }

    fun setBackgroundColor(color: Int) {
        val recentColors =
            state.value.recentBackgroundColors
                .toMutableList()
                .also { it.add(0, color) }
                .distinct()
                .take(6)
        state.update { it.copy(style = it.style.copy(backgroundColor = color), recentBackgroundColors = recentColors) }
        userSettings.qrRecentBackgroundColors = recentColors
        regenerate()
    }

    fun onSave() {
        val current = state.value
        qrCodeExport.save(current.qrCode, current.url, userSettings.imageSaveLocation)
    }

    fun onDocumentPicked(pick: DocumentPick) {
        qrCodeExport.onDocumentPicked(pick, state.value.qrCode)
    }

    fun onCopy() {
        qrCodeExport.copy(state.value.qrCode)
    }

    fun onShare() {
        qrCodeExport.share(state.value.qrCode, ShareTarget.SHARE_SHEET)
    }

    fun onExportHandled(result: BitmapExport.Result) {
        qrCodeExport.onHandled(result)
    }

    private fun initialState(): QrUiState {
        val url = userSettings.qrURL
        val recentForegroundColors = userSettings.qrRecentForegroundColors
        val recentBackgroundColors = userSettings.qrRecentBackgroundColors
        val style =
            QRCodeStyle(
                size = userSettings.qrSize,
                foregroundColor = recentForegroundColors.first(),
                backgroundColor = recentBackgroundColors.first(),
                tintAnchor = userSettings.qrTintAnchor,
                tintBorder = userSettings.qrTintBorder,
                icon = userSettings.qrIcon,
                roundedFrame = userSettings.qrFrame,
            )
        return QrUiState(
            url = url,
            qrCode = generateQRCode(url, style),
            style = style,
            recentForegroundColors = recentForegroundColors,
            recentBackgroundColors = recentBackgroundColors,
        )
    }

    private fun updateStyle(transform: (QRCodeStyle) -> QRCodeStyle) {
        state.update { it.copy(style = transform(it.style)) }
        regenerate()
    }

    // Runs synchronously on the calling (Main) dispatcher. QR encoding + canvas drawing is only a
    // few ms, and dispatching it to a background dispatcher makes cancellation ineffective mid-job
    // on rapid slider/text changes, producing concurrent bitmap allocations instead of preventing them.
    private fun regenerate() {
        val current = state.value
        state.update { it.copy(qrCode = generateQRCode(current.url, current.style)) }
    }
}

data class QrUiState(
    val url: String,
    val qrCode: Bitmap,
    val style: QRCodeStyle,
    val recentForegroundColors: List<Int>,
    val recentBackgroundColors: List<Int>,
)
