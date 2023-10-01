package de.lemke.oneurl

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import dagger.hilt.android.HiltAndroidApp
import de.lemke.oneurl.domain.GetUserSettingsUseCase
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Main entry point into the application process.
 * Registered in the AndroidManifest.xml file.
 */
@HiltAndroidApp
class App : Application() {

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject

    override fun onCreate() {
        super.onCreate()
        runBlocking {
            val userSettings = getUserSettings()
            if (!userSettings.autoDarkMode) {
                if (userSettings.darkMode) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                else AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }
    }
}
