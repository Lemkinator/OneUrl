package de.lemke.oneurl.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lemke.oneurl.domain.GenerateQRCodeUseCase
import de.lemke.oneurl.domain.GetUserSettingsUseCase
import de.lemke.oneurl.domain.UpdateUserSettingsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class GenerateQRCodeViewModel @Inject constructor(
    private val getUserSettings: GetUserSettingsUseCase,
    private val updateUserSettings: UpdateUserSettingsUseCase,
    private val generateQRCode: GenerateQRCodeUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(QrUiState())
    val state: StateFlow<QrUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val s = getUserSettings()
            _state.update {
                it.copy(
                    url = s.qrURL,
                    size = s.qrSize,
                    foregroundColor = s.qrRecentForegroundColors.first(),
                    backgroundColor = s.qrRecentBackgroundColors.first(),
                    tintAnchor = s.qrTintAnchor,
                    tintBorder = s.qrTintBorder,
                    icon = s.qrIcon,
                    roundedFrame = s.qrFrame,
                    recentForegroundColors = s.qrRecentForegroundColors,
                    recentBackgroundColors = s.qrRecentBackgroundColors,
                    isLoading = false,
                )
            }
            regenerateQR()
        }
    }

    fun setUrl(url: String) {
        _state.update { it.copy(url = url) }
        viewModelScope.launch {
            updateUserSettings { it.copy(qrURL = url) }
            regenerateQR()
        }
    }

    fun setSize(size: Int) {
        _state.update { it.copy(size = size) }
        viewModelScope.launch {
            updateUserSettings { it.copy(qrSize = size) }
            regenerateQR()
        }
    }

    fun setRoundedFrame(enabled: Boolean) {
        _state.update { it.copy(roundedFrame = enabled) }
        viewModelScope.launch {
            updateUserSettings { it.copy(qrFrame = enabled) }
            regenerateQR()
        }
    }

    fun setIcon(enabled: Boolean) {
        _state.update { it.copy(icon = enabled) }
        viewModelScope.launch {
            updateUserSettings { it.copy(qrIcon = enabled) }
            regenerateQR()
        }
    }

    fun setTintBorder(enabled: Boolean) {
        _state.update { it.copy(tintBorder = enabled) }
        viewModelScope.launch {
            updateUserSettings { it.copy(qrTintBorder = enabled) }
            regenerateQR()
        }
    }

    fun setTintAnchor(enabled: Boolean) {
        _state.update { it.copy(tintAnchor = enabled) }
        viewModelScope.launch {
            updateUserSettings { it.copy(qrTintAnchor = enabled) }
            regenerateQR()
        }
    }

    fun setForegroundColor(color: Int) {
        viewModelScope.launch {
            val recentColors = _state.value.recentForegroundColors.toMutableList().also { it.add(0, color) }.distinct().take(6)
            _state.update { it.copy(foregroundColor = color, recentForegroundColors = recentColors) }
            updateUserSettings { it.copy(qrRecentForegroundColors = recentColors) }
            regenerateQR()
        }
    }

    fun setBackgroundColor(color: Int) {
        viewModelScope.launch {
            val recentColors = _state.value.recentBackgroundColors.toMutableList().also { it.add(0, color) }.distinct().take(6)
            _state.update { it.copy(backgroundColor = color, recentBackgroundColors = recentColors) }
            updateUserSettings { it.copy(qrRecentBackgroundColors = recentColors) }
            regenerateQR()
        }
    }

    private suspend fun regenerateQR() {
        val s = _state.value
        val qr = withContext(Dispatchers.Default) {
            generateQRCode(s.url, s.size, s.foregroundColor, s.backgroundColor, s.tintAnchor, s.tintBorder, s.icon, s.roundedFrame)
        }
        _state.update { it.copy(qrCode = qr) }
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
