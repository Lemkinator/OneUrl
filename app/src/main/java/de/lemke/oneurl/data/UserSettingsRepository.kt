package de.lemke.oneurl.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.domain.model.ShortURLProviderCompanion
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
            it[KEY_SELECTED_SHORT_URL_PROVIDER] = newSettings.selectedShortURLProvider.name
            it[KEY_LAST_ALIAS] = newSettings.lastAlias
            it[KEY_LAST_URL] = newSettings.lastURL
            it[KEY_LAST_DESCRIPTION] = newSettings.lastDescription
            it[KEY_QR_URL] = newSettings.qrURL
            it[KEY_QR_RECENT_BACKGROUND_COLORS] = newSettings.qrRecentBackgroundColors.joinToString(",")
            it[KEY_QR_RECENT_FOREGROUND_COLORS] = newSettings.qrRecentForegroundColors.joinToString(",")
            it[KEY_QR_SIZE] = newSettings.qrSize
            it[KEY_QR_FRAME] = newSettings.qrFrame
            it[KEY_QR_ICON] = newSettings.qrIcon
            it[KEY_QR_TINT_ANCHOR] = newSettings.qrTintAnchor
            it[KEY_QR_TINT_BORDER] = newSettings.qrTintBorder
            it[KEY_SAVE_LOCATION] = newSettings.saveLocation.ordinal
            it[KEY_CURRENT_CATEGORY] = newSettings.currentCategory
            it[KEY_LAST_IN_APP_REVIEW_REQUEST] = newSettings.lastInAppReviewRequest
        }
        return settingsFromPreferences(prefs)
    }


    private fun settingsFromPreferences(prefs: Preferences) = UserSettings(
        lastVersionCode = prefs[KEY_LAST_VERSION_CODE] ?: -1,
        lastVersionName = prefs[KEY_LAST_VERSION_NAME] ?: "0.0",
        darkMode = prefs[KEY_DARK_MODE] == true,
        autoDarkMode = prefs[KEY_AUTO_DARK_MODE] == true,
        tosAccepted = prefs[KEY_TOS_ACCEPTED] == true,
        devModeEnabled = prefs[KEY_DEV_MODE_ENABLED] == true,
        search = prefs[KEY_SEARCH] ?: "",
        showOnlyFavorites = prefs[KEY_SHOW_ONLY_FAVORITES] == true,
        selectedShortURLProvider = ShortURLProviderCompanion.fromStringOrDefault(prefs[KEY_SELECTED_SHORT_URL_PROVIDER]),
        lastAlias = prefs[KEY_LAST_ALIAS] ?: "",
        lastURL = prefs[KEY_LAST_URL] ?: "",
        lastDescription = prefs[KEY_LAST_DESCRIPTION] ?: "",
        qrURL = prefs[KEY_QR_URL] ?: "",
        qrRecentBackgroundColors = prefs[KEY_QR_RECENT_BACKGROUND_COLORS]?.split(",")?.map { it.toInt() } ?: listOf(-1),
        qrRecentForegroundColors = prefs[KEY_QR_RECENT_FOREGROUND_COLORS]?.split(",")?.map { it.toInt() } ?: listOf(-16777216),
        qrSize = prefs[KEY_QR_SIZE] ?: 512,
        qrFrame = prefs[KEY_QR_FRAME] == true,
        qrIcon = prefs[KEY_QR_ICON] == true,
        qrTintAnchor = prefs[KEY_QR_TINT_ANCHOR] == true,
        qrTintBorder = prefs[KEY_QR_TINT_BORDER] == true,
        saveLocation = SaveLocation.entries[prefs[KEY_SAVE_LOCATION] ?: SaveLocation.default.ordinal],
        currentCategory = prefs[KEY_CURRENT_CATEGORY] ?: "",
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
        private val KEY_SELECTED_SHORT_URL_PROVIDER = stringPreferencesKey("selectedShortURLProvider")
        private val KEY_LAST_ALIAS = stringPreferencesKey("lastAlias")
        private val KEY_LAST_URL = stringPreferencesKey("lastURL")
        private val KEY_LAST_DESCRIPTION = stringPreferencesKey("lastDescription")
        private val KEY_QR_URL = stringPreferencesKey("qrURL")
        private val KEY_QR_RECENT_BACKGROUND_COLORS = stringPreferencesKey("qrRecentBackgroundColors")
        private val KEY_QR_RECENT_FOREGROUND_COLORS = stringPreferencesKey("qrRecentForegroundColors")
        private val KEY_QR_SIZE = intPreferencesKey("qrSize")
        private val KEY_QR_FRAME = booleanPreferencesKey("qrFrame")
        private val KEY_QR_ICON = booleanPreferencesKey("qrIcon")
        private val KEY_QR_TINT_ANCHOR = booleanPreferencesKey("qrTintAnchor")
        private val KEY_QR_TINT_BORDER = booleanPreferencesKey("qrTintBorder")
        private val KEY_SAVE_LOCATION = intPreferencesKey("saveLocation")
        private val KEY_CURRENT_CATEGORY = stringPreferencesKey("currentCategory")
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
    /** selected ShortURLProvider */
    val selectedShortURLProvider: ShortURLProvider,
    /** last alias */
    val lastAlias: String,
    /** last url */
    val lastURL: String,
    /** last description */
    val lastDescription: String,
    /** last generated QR URL */
    val qrURL: String,
    /** recent background colors of qr code */
    val qrRecentBackgroundColors: List<Int>,
    /** recent foreground colors of qr */
    val qrRecentForegroundColors: List<Int>,
    /** qr code size */
    val qrSize: Int,
    /** qr code frame */
    val qrFrame: Boolean,
    /** qr code icon */
    val qrIcon: Boolean,
    /** qr code tint anchor */
    val qrTintAnchor: Boolean,
    /** qr code tint border */
    val qrTintBorder: Boolean,
    /** save location */
    val saveLocation: SaveLocation,
    /** current category */
    val currentCategory: String,
    /** last time in app review was requested */
    val lastInAppReviewRequest: Long,
)

enum class SaveLocation {
    CUSTOM,
    DOWNLOADS,
    PICTURES,
    DCIM,
    ;

    companion object {
        val default = CUSTOM

        fun fromStringOrDefault(string: String?): SaveLocation = entries.firstOrNull { it.toString() == string } ?: default
    }

    fun toLocalizedString(context: Context): String {
        return when (this) {
            CUSTOM -> context.getString(R.string.custom)
            DOWNLOADS -> "Downloads"
            PICTURES -> "Pictures"
            DCIM -> "DCIM"
        }
    }
}
