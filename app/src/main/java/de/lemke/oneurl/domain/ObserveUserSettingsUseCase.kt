package de.lemke.oneurl.domain


import de.lemke.oneurl.data.UserSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class ObserveUserSettingsUseCase @Inject constructor(
    private val userSettingsRepository: UserSettingsRepository,
) {
    operator fun invoke() = userSettingsRepository.observeSettings().flowOn(Dispatchers.Default)
}