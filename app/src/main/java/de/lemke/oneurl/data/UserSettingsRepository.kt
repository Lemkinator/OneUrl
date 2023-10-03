package de.lemke.oneurl.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import de.lemke.oneurl.domain.model.ShortUrlProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Provides CRUD operations for user settings. */
class UserSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    /** Returns the current user settings. */
    suspend fun getSettings(): UserSettings = dataStore.data.map(::settingsFromPreferences).first()

    /** Emits the current user settings. */
    fun observeSettings(): Flow<UserSettings> = dataStore.data.map(::settingsFromPreferences)

    /**
     * Updates the current user settings and returns the new settings.
     * @param f Invoked with the current settings; The settings returned from this function will replace the current ones.
     */
    suspend fun updateSettings(f: (UserSettings) -> UserSettings): UserSettings {
        val prefs = dataStore.edit {
            val newSettings = f(settingsFromPreferences(it))
            it[KEY_LAST_VERSION_CODE] = newSettings.lastVersionCode
            it[KEY_LAST_VERSION_NAME] = newSettings.lastVersionName
            it[KEY_DARK_MODE] = newSettings.darkMode
            it[KEY_AUTO_DARK_MODE] = newSettings.autoDarkMode
            it[KEY_TOS_ACCEPTED] = newSettings.tosAccepted
            it[KEY_DEV_MODE_ENABLED] = newSettings.devModeEnabled
            it[KEY_SEARCH] = newSettings.search
            it[KEY_SHOW_ONLY_FAVORITES] = newSettings.showOnlyFavorites
            it[KEY_SELECTED_SHORT_URL_PROVIDER] = newSettings.selectedShortUrlProvider.toString()
            it[KEY_LAST_IN_APP_REVIEW_REQUEST] = newSettings.lastInAppReviewRequest
        }
        return settingsFromPreferences(prefs)
    }


    private fun settingsFromPreferences(prefs: Preferences) = UserSettings(
        lastVersionCode = prefs[KEY_LAST_VERSION_CODE] ?: -1,
        lastVersionName = prefs[KEY_LAST_VERSION_NAME] ?: "0.0",
        darkMode = prefs[KEY_DARK_MODE] ?: false,
        autoDarkMode = prefs[KEY_AUTO_DARK_MODE] ?: true,
        tosAccepted = prefs[KEY_TOS_ACCEPTED] ?: false,
        devModeEnabled = prefs[KEY_DEV_MODE_ENABLED] ?: false,
        search = prefs[KEY_SEARCH] ?: "",
        showOnlyFavorites = prefs[KEY_SHOW_ONLY_FAVORITES] ?: false,
        selectedShortUrlProvider = ShortUrlProvider.fromStringOrDefault(prefs[KEY_SELECTED_SHORT_URL_PROVIDER]),
        lastInAppReviewRequest = prefs[KEY_LAST_IN_APP_REVIEW_REQUEST] ?: System.currentTimeMillis(),
    )

    private companion object {
        private val KEY_LAST_VERSION_CODE = intPreferencesKey("lastVersionCode")
        private val KEY_LAST_VERSION_NAME = stringPreferencesKey("lastVersionName")
        private val KEY_DARK_MODE = booleanPreferencesKey("darkMode")
        private val KEY_AUTO_DARK_MODE = booleanPreferencesKey("autoDarkMode")
        private val KEY_TOS_ACCEPTED = booleanPreferencesKey("tosAccepted")
        private val KEY_DEV_MODE_ENABLED = booleanPreferencesKey("devModeEnabled")
        private val KEY_SEARCH = stringPreferencesKey("search")
        private val KEY_SHOW_ONLY_FAVORITES = booleanPreferencesKey("showOnlyFavorites")
        private val KEY_SELECTED_SHORT_URL_PROVIDER = stringPreferencesKey("selectedShortUrlProvider")
        private val KEY_LAST_IN_APP_REVIEW_REQUEST = longPreferencesKey("lastInAppReviewRequest")
    }
}

/** Settings associated with the current user. */
data class UserSettings(
    /** devMode enabled */
    val devModeEnabled: Boolean,
    /** Dark Mode enabled */
    val darkMode: Boolean,
    /** Auto Dark Mode enabled */
    val autoDarkMode: Boolean,
    /** Last App-Version-Code */
    val lastVersionCode: Int,
    /** Last App-Version-Name */
    val lastVersionName: String,
    /** terms of service accepted by user */
    val tosAccepted: Boolean,
    /** search */
    val search: String,
    /** show only favorites */
    val showOnlyFavorites: Boolean,
    /** selected ShortUrlProvider */
    val selectedShortUrlProvider: ShortUrlProvider,
    /** last time in app review was requested */
    val lastInAppReviewRequest: Long,
)
