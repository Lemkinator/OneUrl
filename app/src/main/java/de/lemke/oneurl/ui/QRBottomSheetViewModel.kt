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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lemke.oneurl.data.QRCodeExporter
import de.lemke.oneurl.data.UserSettings
import de.lemke.oneurl.domain.GetQRCodeUseCase
import de.lemke.oneurl.ui.QRBottomSheet.Companion.KEY_SHORT_URL
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QRBottomSheetUiState(
    val shortURL: String = "",
    val qrCode: Bitmap? = null,
)

@HiltViewModel
class QRBottomSheetViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getQRCode: GetQRCodeUseCase,
    private val userSettings: UserSettings,
    exporter: QRCodeExporter,
) : ViewModel() {
    private val qrCodeExport = QRCodeExportStateHolder(viewModelScope, exporter)
    val state: StateFlow<QRBottomSheetUiState>
        field = MutableStateFlow(QRBottomSheetUiState(shortURL = savedStateHandle.get<String>(KEY_SHORT_URL).orEmpty()))
    val export: StateFlow<QRCodeExport> = qrCodeExport.state

    init {
        val shortURL = state.value.shortURL
        if (shortURL.isNotEmpty()) {
            viewModelScope.launch {
                val qrCode = getQRCode(shortURL)
                state.update { it.copy(qrCode = qrCode) }
            }
        }
    }

    fun onSave() {
        val current = state.value
        val qrCode = current.qrCode ?: return
        qrCodeExport.save(qrCode, current.shortURL, userSettings.imageSaveLocation)
    }

    fun onDocumentPicked(pick: DocumentPick) {
        qrCodeExport.onDocumentPicked(pick) {
            if (state.value.shortURL.isEmpty()) null else state.mapNotNull { it.qrCode }.first()
        }
    }

    fun onShare() {
        val qrCode = state.value.qrCode ?: return
        qrCodeExport.share(qrCode, ShareTarget.SHARE_SHEET)
    }

    fun onQuickShare() {
        val qrCode = state.value.qrCode ?: return
        qrCodeExport.share(qrCode, ShareTarget.QUICK_SHARE)
    }

    fun onExportHandled(result: QRCodeExport.Result) {
        qrCodeExport.onHandled(result)
    }
}
