package de.lemke.oneurl.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lemke.commonutils.di.DefaultDispatcher
import de.lemke.oneurl.domain.GenerateQRCodeUseCase
import de.lemke.oneurl.domain.GetUserSettingsUseCase
import de.lemke.oneurl.domain.UpdateUserSettingsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher

@HiltViewModel
class GenerateQRCodeViewModel @Inject constructor(
    private val getUserSettings: GetUserSettingsUseCase,
    private val updateUserSettings: UpdateUserSettingsUseCase,
    private val generateQRCode: GenerateQRCodeUseCase,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val _state = MutableStateFlow(QrUiState())
    val state: StateFlow<QrUiState> = _state.asStateFlow()
    private var regenJob: Job? = null

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
            launchRegenerate()
        }
    }

    fun setUrl(url: String) {
        _state.update { it.copy(url = url) }
        viewModelScope.launch { updateUserSettings { it.copy(qrURL = url) } }
        launchRegenerate()
    }

    fun setSize(size: Int) {
        _state.update { it.copy(size = size) }
        viewModelScope.launch { updateUserSettings { it.copy(qrSize = size) } }
        launchRegenerate()
    }

    fun setRoundedFrame(enabled: Boolean) {
        _state.update { it.copy(roundedFrame = enabled) }
        viewModelScope.launch { updateUserSettings { it.copy(qrFrame = enabled) } }
        launchRegenerate()
    }

    fun setIcon(enabled: Boolean) {
        _state.update { it.copy(icon = enabled) }
        viewModelScope.launch { updateUserSettings { it.copy(qrIcon = enabled) } }
        launchRegenerate()
    }

    fun setTintBorder(enabled: Boolean) {
        _state.update { it.copy(tintBorder = enabled) }
        viewModelScope.launch { updateUserSettings { it.copy(qrTintBorder = enabled) } }
        launchRegenerate()
    }

    fun setTintAnchor(enabled: Boolean) {
        _state.update { it.copy(tintAnchor = enabled) }
        viewModelScope.launch { updateUserSettings { it.copy(qrTintAnchor = enabled) } }
        launchRegenerate()
    }

    fun setForegroundColor(color: Int) {
        val recentColors = _state.value.recentForegroundColors.toMutableList().also { it.add(0, color) }.distinct().take(6)
        _state.update { it.copy(foregroundColor = color, recentForegroundColors = recentColors) }
        viewModelScope.launch { updateUserSettings { it.copy(qrRecentForegroundColors = recentColors) } }
        launchRegenerate()
    }

    fun setBackgroundColor(color: Int) {
        val recentColors = _state.value.recentBackgroundColors.toMutableList().also { it.add(0, color) }.distinct().take(6)
        _state.update { it.copy(backgroundColor = color, recentBackgroundColors = recentColors) }
        viewModelScope.launch { updateUserSettings { it.copy(qrRecentBackgroundColors = recentColors) } }
        launchRegenerate()
    }

    private fun launchRegenerate() {
        regenJob?.cancel()
        regenJob = viewModelScope.launch {
            val s = _state.value
            val qr = withContext(defaultDispatcher) {
                generateQRCode(s.url, s.size, s.foregroundColor, s.backgroundColor, s.tintAnchor, s.tintBorder, s.icon, s.roundedFrame)
            }
            _state.update { it.copy(qrCode = qr) }
        }
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
