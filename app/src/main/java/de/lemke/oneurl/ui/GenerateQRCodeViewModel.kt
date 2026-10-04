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
import android.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lemke.oneurl.data.QRCodeExporter
import de.lemke.oneurl.data.UserSettings
import de.lemke.oneurl.domain.GenerateQRCodeUseCase
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
    exporter: QRCodeExporter,
) : ViewModel() {
    private val qrCodeExport = QRCodeExportStateHolder(viewModelScope, exporter)
    val state: StateFlow<QrUiState>
        field = MutableStateFlow(initialState())
    val export: StateFlow<QRCodeExport> = qrCodeExport.state
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
        state.update { it.copy(size = size) }
        regenerate()
        sizeSaveJob?.cancel()
        sizeSaveJob =
            viewModelScope.launch {
                delay(300.milliseconds)
                userSettings.qrSize = size
            }
    }

    fun setRoundedFrame(enabled: Boolean) {
        state.update { it.copy(roundedFrame = enabled) }
        userSettings.qrFrame = enabled
        regenerate()
    }

    fun setIcon(enabled: Boolean) {
        state.update { it.copy(icon = enabled) }
        userSettings.qrIcon = enabled
        regenerate()
    }

    fun setTintBorder(enabled: Boolean) {
        state.update { it.copy(tintBorder = enabled) }
        userSettings.qrTintBorder = enabled
        regenerate()
    }

    fun setTintAnchor(enabled: Boolean) {
        state.update { it.copy(tintAnchor = enabled) }
        userSettings.qrTintAnchor = enabled
        regenerate()
    }

    fun setForegroundColor(color: Int) {
        val recentColors =
            state.value.recentForegroundColors
                .toMutableList()
                .also { it.add(0, color) }
                .distinct()
                .take(6)
        state.update { it.copy(foregroundColor = color, recentForegroundColors = recentColors) }
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
        state.update { it.copy(backgroundColor = color, recentBackgroundColors = recentColors) }
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

    fun onExportHandled(result: QRCodeExport.Result) {
        qrCodeExport.onHandled(result)
    }

    private fun initialState(): QrUiState {
        val url = userSettings.qrURL
        val size = userSettings.qrSize
        val recentForegroundColors = userSettings.qrRecentForegroundColors
        val recentBackgroundColors = userSettings.qrRecentBackgroundColors
        val foregroundColor = recentForegroundColors.firstOrNull() ?: Color.BLACK
        val backgroundColor = recentBackgroundColors.firstOrNull() ?: Color.WHITE
        val tintAnchor = userSettings.qrTintAnchor
        val tintBorder = userSettings.qrTintBorder
        val icon = userSettings.qrIcon
        val roundedFrame = userSettings.qrFrame
        return QrUiState(
            url = url,
            qrCode = generateQRCode(url, size, foregroundColor, backgroundColor, tintAnchor, tintBorder, icon, roundedFrame),
            size = size,
            foregroundColor = foregroundColor,
            backgroundColor = backgroundColor,
            tintAnchor = tintAnchor,
            tintBorder = tintBorder,
            icon = icon,
            roundedFrame = roundedFrame,
            recentForegroundColors = recentForegroundColors,
            recentBackgroundColors = recentBackgroundColors,
            isLoading = false,
        )
    }

    // Runs synchronously on the calling (Main) dispatcher. QR encoding + canvas drawing is only a
    // few ms, and dispatching it to a background dispatcher makes cancellation ineffective mid-job
    // on rapid slider/text changes, producing concurrent bitmap allocations instead of preventing them.
    private fun regenerate() {
        val s = state.value
        val qr = generateQRCode(s.url, s.size, s.foregroundColor, s.backgroundColor, s.tintAnchor, s.tintBorder, s.icon, s.roundedFrame)
        state.update { it.copy(qrCode = qr) }
    }
}

data class QrUiState(
    val url: String = "",
    val qrCode: Bitmap,
    val size: Int = 512,
    val foregroundColor: Int = Color.BLACK,
    val backgroundColor: Int = Color.WHITE,
    val tintAnchor: Boolean = false,
    val tintBorder: Boolean = false,
    val icon: Boolean = true,
    val roundedFrame: Boolean = true,
    val recentForegroundColors: List<Int> = listOf(Color.BLACK),
    val recentBackgroundColors: List<Int> = listOf(Color.WHITE),
    val isLoading: Boolean = true,
)
