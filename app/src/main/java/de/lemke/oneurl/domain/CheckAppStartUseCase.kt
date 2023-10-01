package de.lemke.oneurl.domain

import android.util.Log
import de.lemke.oneurl.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class CheckAppStartUseCase @Inject constructor(
    private val getUserSettings: GetUserSettingsUseCase,
    private val updateUserSettings: UpdateUserSettingsUseCase,
) {
    suspend operator fun invoke(): AppStart =
        withContext(Dispatchers.Default) {
            val userSettings = getUserSettings()
            val versionCode: Int = BuildConfig.VERSION_CODE
            val versionName: String = BuildConfig.VERSION_NAME
            updateUserSettings { it.copy(lastVersionCode = versionCode, lastVersionName = versionName) }
            Log.d("CheckAppStart", "Current version code: $versionCode , last version code: ${userSettings.lastVersionCode}")
            Log.d("CheckAppStart", "Current version name: $versionName , last version name: ${userSettings.lastVersionName}")
            if (userSettings.lastVersionCode < 1) updateUserSettings { it.copy(tosAccepted = false) }
            return@withContext when {
                userSettings.lastVersionCode == -1 -> AppStart.FIRST_TIME
                userSettings.lastVersionCode < versionCode -> AppStart.FIRST_TIME_VERSION
                userSettings.lastVersionCode > versionCode -> {
                    Log.w(
                        "checkAppStart",
                        "Current version code ($versionCode) is less then the one recognized on " +
                                "last startup (${userSettings.lastVersionCode}). Defensively assuming normal app start."
                    )
                    AppStart.NORMAL
                }
                else -> AppStart.NORMAL
            }
        }

}

enum class AppStart {
    FIRST_TIME, FIRST_TIME_VERSION, NORMAL
}
