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
import de.lemke.commonutils.ui.utils.BitmapExport
import de.lemke.commonutils.ui.utils.BitmapExportStateHolder
import de.lemke.commonutils.ui.utils.BitmapExporter
import de.lemke.commonutils.ui.utils.DocumentPick
import de.lemke.commonutils.ui.utils.ShareTarget
import de.lemke.oneurl.data.UserSettings
import de.lemke.oneurl.domain.GetQRCodeUseCase
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShortURLQRCodeUiState(
    val shortURL: String = "",
    val qrCode: Bitmap? = null,
)

@HiltViewModel
class ShortURLQRCodeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getQRCode: GetQRCodeUseCase,
    private val userSettings: UserSettings,
    exporter: BitmapExporter,
) : ViewModel() {
    private val qrCodeExport = BitmapExportStateHolder(viewModelScope, exporter)
    val state: StateFlow<ShortURLQRCodeUiState>
        field = MutableStateFlow(ShortURLQRCodeUiState(shortURL = savedStateHandle.get<String>(KEY_SHORT_URL).orEmpty()))
    val export: StateFlow<BitmapExport> = qrCodeExport.state

    private var loadJob: Job? = null

    /** Renders the QR code of the short URL in the saved state, once. A host calls it when the short URL is known to exist. */
    fun load() {
        val shortURL = state.value.shortURL
        if (shortURL.isEmpty() || loadJob != null) return
        loadJob =
            viewModelScope.launch {
                val qrCode = getQRCode(shortURL)
                state.update { it.copy(qrCode = qrCode) }
            }
    }

    fun onSave() {
        val current = state.value
        val qrCode = current.qrCode ?: return
        qrCodeExport.save(qrCode, current.shortURL, userSettings.imageSaveLocation)
    }

    fun onDocumentPicked(pick: DocumentPick) {
        qrCodeExport.onDocumentPicked(pick, state.value.qrCode)
    }

    fun onCopy() {
        val qrCode = state.value.qrCode ?: return
        qrCodeExport.copy(qrCode)
    }

    fun onShare() {
        val qrCode = state.value.qrCode ?: return
        qrCodeExport.share(qrCode, ShareTarget.SHARE_SHEET)
    }

    fun onQuickShare() {
        val qrCode = state.value.qrCode ?: return
        qrCodeExport.share(qrCode, ShareTarget.QUICK_SHARE)
    }

    fun onExportHandled(result: BitmapExport.Result) {
        qrCodeExport.onHandled(result)
    }

    companion object {
        const val KEY_SHORT_URL = "key_short_url"
    }
}
