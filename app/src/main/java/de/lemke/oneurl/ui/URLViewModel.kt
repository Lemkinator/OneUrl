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
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lemke.oneurl.data.QRCodeExporter
import de.lemke.oneurl.data.UserSettings
import de.lemke.oneurl.domain.DeleteURLUseCase
import de.lemke.oneurl.domain.GetQRCodeUseCase
import de.lemke.oneurl.domain.GetURLUseCase
import de.lemke.oneurl.domain.GetVisitCountUseCase
import de.lemke.oneurl.domain.UpdateURLUseCase
import de.lemke.oneurl.domain.model.URL
import de.lemke.oneurl.ui.URLActivity.Companion.KEY_SHORTURL
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class URLViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getURL: GetURLUseCase,
    private val updateURL: UpdateURLUseCase,
    private val deleteURL: DeleteURLUseCase,
    private val getVisitCount: GetVisitCountUseCase,
    private val getQRCode: GetQRCodeUseCase,
    private val userSettings: UserSettings,
    exporter: QRCodeExporter,
) : ViewModel() {
    private val qrCodeExport = QRCodeExportStateHolder(viewModelScope, exporter)
    val state: StateFlow<UrlDetailUiState>
        field = MutableStateFlow(UrlDetailUiState())
    val export: StateFlow<QRCodeExport> = qrCodeExport.state

    private val _events = Channel<UrlDetailEvent>(Channel.BUFFERED)
    val events: Flow<UrlDetailEvent> = _events.receiveAsFlow()

    init {
        val shortURL = savedStateHandle.get<String>(KEY_SHORTURL) ?: ""
        viewModelScope.launch {
            val url = getURL(shortURL)
            if (url == null) {
                _events.send(UrlDetailEvent.NotFound)
                return@launch
            }
            state.update { it.copy(url = url, isLoading = false) }
            refreshVisitCount()
            val qrCode = getQRCode(url.shortURL)
            state.update { it.copy(qrCode = qrCode) }
        }
    }

    fun toggleFavorite() {
        val url = state.value.url ?: return
        val updated = url.copy(favorite = !url.favorite)
        state.update { it.copy(url = updated) }
        viewModelScope.launch { updateURL(updated) }
    }

    @Suppress("TooGenericExceptionCaught")
    fun refreshVisitCount() {
        val url = state.value.url ?: return
        if (state.value.isRefreshingVisits) return
        state.update { it.copy(isRefreshingVisits = true) }
        viewModelScope.launch {
            try {
                val count = getVisitCount(url)
                state.update { it.copy(visitCount = count, isRefreshingVisits = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh visit count", e)
                state.update { it.copy(isRefreshingVisits = false) }
            }
        }
    }

    fun delete() {
        val url = state.value.url ?: return
        viewModelScope.launch {
            deleteURL(url)
            _events.send(UrlDetailEvent.Deleted)
        }
    }

    fun onSaveQRCode() {
        val current = state.value
        val url = current.url ?: return
        val qrCode = current.qrCode ?: return
        qrCodeExport.save(qrCode, url.shortURL, userSettings.imageSaveLocation)
    }

    fun onDocumentPicked(pick: DocumentPick) {
        qrCodeExport.onDocumentPicked(pick, state.value.qrCode)
    }

    fun onCopyQRCode() {
        val qrCode = state.value.qrCode ?: return
        qrCodeExport.copy(qrCode)
    }

    fun onShareQRCode() {
        val qrCode = state.value.qrCode ?: return
        qrCodeExport.share(qrCode, ShareTarget.SHARE_SHEET)
    }

    fun onExportHandled(result: QRCodeExport.Result) {
        qrCodeExport.onHandled(result)
    }

    companion object {
        private const val TAG = "URLViewModel"
    }
}

data class UrlDetailUiState(
    val url: URL? = null,
    val isLoading: Boolean = true,
    val visitCount: Int? = null,
    val isRefreshingVisits: Boolean = false,
    val qrCode: Bitmap? = null,
)

sealed class UrlDetailEvent {
    data object NotFound : UrlDetailEvent()

    data object Deleted : UrlDetailEvent()
}
