package de.lemke.oneurl.domain

import de.lemke.oneurl.data.UserSettings
import de.lemke.oneurl.data.UserSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class UpdateUserSettingsUseCase @Inject constructor(
    private val userSettingsRepository: UserSettingsRepository,
) {
    suspend operator fun invoke(f: (UserSettings) -> UserSettings) = withContext(Dispatchers.Default) {
        userSettingsRepository.updateSettings(f)
    }
}