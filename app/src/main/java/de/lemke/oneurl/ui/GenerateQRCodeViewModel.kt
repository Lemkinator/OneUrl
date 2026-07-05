package de.lemke.oneurl.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lemke.oneurl.domain.GenerateQRCodeUseCase
import de.lemke.oneurl.domain.GetUserSettingsUseCase
import de.lemke.oneurl.domain.UpdateUserSettingsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GenerateQRCodeViewModel @Inject constructor(
    private val getUserSettings: GetUserSettingsUseCase,
    private val updateUserSettings: UpdateUserSettingsUseCase,
    private val generateQRCode: GenerateQRCodeUseCase,
) : ViewModel() {
    val state: StateFlow<QrUiState>
        field = MutableStateFlow(QrUiState())
    private var urlSaveJob: Job? = null
    private var sizeSaveJob: Job? = null

    init {
        viewModelScope.launch {
            val s = getUserSettings()
            state.update {
                it.copy(
                    url = s.qrURL,
                    size = s.qrSize,
                    foregroundColor = s.qrRecentForegroundColors.firstOrNull() ?: -16777216,
                    backgroundColor = s.qrRecentBackgroundColors.firstOrNull() ?: -1,
                    tintAnchor = s.qrTintAnchor,
                    tintBorder = s.qrTintBorder,
                    icon = s.qrIcon,
                    roundedFrame = s.qrFrame,
                    recentForegroundColors = s.qrRecentForegroundColors,
                    recentBackgroundColors = s.qrRecentBackgroundColors,
                    isLoading = false,
                )
            }
            regenerate()
        }
    }

    fun setUrl(url: String) {
        state.update { it.copy(url = url) }
        regenerate()
        urlSaveJob?.cancel()
        urlSaveJob = viewModelScope.launch {
            delay(300)
            updateUserSettings { it.copy(qrURL = url) }
        }
    }

    fun setSize(size: Int) {
        state.update { it.copy(size = size) }
        regenerate()
        sizeSaveJob?.cancel()
        sizeSaveJob = viewModelScope.launch {
            delay(300)
            updateUserSettings { it.copy(qrSize = size) }
        }
    }

    fun setRoundedFrame(enabled: Boolean) {
        state.update { it.copy(roundedFrame = enabled) }
        viewModelScope.launch { updateUserSettings { it.copy(qrFrame = enabled) } }
        regenerate()
    }

    fun setIcon(enabled: Boolean) {
        state.update { it.copy(icon = enabled) }
        viewModelScope.launch { updateUserSettings { it.copy(qrIcon = enabled) } }
        regenerate()
    }

    fun setTintBorder(enabled: Boolean) {
        state.update { it.copy(tintBorder = enabled) }
        viewModelScope.launch { updateUserSettings { it.copy(qrTintBorder = enabled) } }
        regenerate()
    }

    fun setTintAnchor(enabled: Boolean) {
        state.update { it.copy(tintAnchor = enabled) }
        viewModelScope.launch { updateUserSettings { it.copy(qrTintAnchor = enabled) } }
        regenerate()
    }

    fun setForegroundColor(color: Int) {
        val recentColors = state.value.recentForegroundColors.toMutableList().also { it.add(0, color) }.distinct().take(6)
        state.update { it.copy(foregroundColor = color, recentForegroundColors = recentColors) }
        viewModelScope.launch { updateUserSettings { it.copy(qrRecentForegroundColors = recentColors) } }
        regenerate()
    }

    fun setBackgroundColor(color: Int) {
        val recentColors = state.value.recentBackgroundColors.toMutableList().also { it.add(0, color) }.distinct().take(6)
        state.update { it.copy(backgroundColor = color, recentBackgroundColors = recentColors) }
        viewModelScope.launch { updateUserSettings { it.copy(qrRecentBackgroundColors = recentColors) } }
        regenerate()
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
    val qrCode: Bitmap? = null,
    val size: Int = 512,
    val foregroundColor: Int = -16777216,
    val backgroundColor: Int = -1,
    val tintAnchor: Boolean = false,
    val tintBorder: Boolean = false,
    val icon: Boolean = true,
    val roundedFrame: Boolean = true,
    val recentForegroundColors: List<Int> = listOf(-16777216),
    val recentBackgroundColors: List<Int> = listOf(-1),
    val isLoading: Boolean = true,
)
