package de.lemke.oneurl.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.preference.DropDownPreference
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceClickListener
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.SaveLocation
import de.lemke.commonutils.deleteAppDataAndExit
import de.lemke.commonutils.openApp
import de.lemke.commonutils.openAppLocaleSettings
import de.lemke.commonutils.openURL
import de.lemke.commonutils.sendEmailBugReport
import de.lemke.commonutils.setCustomBackPressAnimation
import de.lemke.commonutils.shareApp
import de.lemke.oneurl.R
import de.lemke.oneurl.databinding.ActivitySettingsBinding
import de.lemke.oneurl.domain.GetUserSettingsUseCase
import de.lemke.oneurl.domain.ObserveUserSettingsUseCase
import de.lemke.oneurl.domain.UpdateUserSettingsUseCase
import dev.oneuiproject.oneui.ktx.addRelativeLinksCard
import dev.oneuiproject.oneui.preference.HorizontalRadioPreference
import dev.oneuiproject.oneui.widget.RelativeLink
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setCustomBackPressAnimation(binding.root)
        if (savedInstanceState == null) supportFragmentManager.beginTransaction().replace(R.id.settings, SettingsFragment()).commit()
    }

    @AndroidEntryPoint
    class SettingsFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener {
        private lateinit var settingsActivity: SettingsActivity
        private lateinit var darkModePref: HorizontalRadioPreference
        private lateinit var autoDarkModePref: SwitchPreferenceCompat
        private lateinit var saveLocationPref: DropDownPreference

        @Inject
        lateinit var getUserSettings: GetUserSettingsUseCase

        @Inject
        lateinit var observeUserSettings: ObserveUserSettingsUseCase

        @Inject
        lateinit var updateUserSettings: UpdateUserSettingsUseCase

        override fun onAttach(context: Context) {
            super.onAttach(context)
            if (activity is SettingsActivity) settingsActivity = activity as SettingsActivity
        }

        override fun onCreatePreferences(bundle: Bundle?, str: String?) {
            addPreferencesFromResource(R.xml.preferences)
        }

        override fun onCreate(bundle: Bundle?) {
            super.onCreate(bundle)
            initPreferences()
        }

        private fun initPreferences() {
            darkModePref = findPreference("dark_mode_pref")!!
            autoDarkModePref = findPreference("dark_mode_auto_pref")!!
            saveLocationPref = findPreference("save_location_pref")!!
            autoDarkModePref.onPreferenceChangeListener = this
            saveLocationPref.onPreferenceChangeListener = this
            darkModePref.onPreferenceChangeListener = this
            darkModePref.setDividerEnabled(false)
            darkModePref.setTouchEffectEnabled(false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                findPreference<PreferenceCategory>("general_pref_cat")!!.isVisible = true
                findPreference<PreferenceScreen>("language_pref")!!.onPreferenceClickListener = OnPreferenceClickListener {
                    openAppLocaleSettings()
                }
            }
            lifecycleScope.launch {
                observeUserSettings().flowWithLifecycle(lifecycle).collectLatest { userSettings ->
                    autoDarkModePref.isChecked = userSettings.autoDarkMode
                    darkModePref.isEnabled = !autoDarkModePref.isChecked
                    darkModePref.value = if (userSettings.darkMode) "1" else "0"
                    findPreference<PreferenceCategory>("dev_options")?.isVisible = userSettings.devModeEnabled
                    saveLocationPref.entries = SaveLocation.getLocalizedEntries(requireContext())
                    saveLocationPref.entryValues = SaveLocation.entryValues
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                        saveLocationPref.value = userSettings.saveLocation.name
                    } else {
                        saveLocationPref.value = SaveLocation.CUSTOM.name
                        saveLocationPref.isEnabled = false
                    }
                }
            }
            findPreference<PreferenceScreen>("privacy_pref")!!.onPreferenceClickListener = OnPreferenceClickListener {
                openURL(getString(R.string.privacy_website))
                true
            }
            findPreference<PreferenceScreen>("tos_pref")!!.onPreferenceClickListener = OnPreferenceClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle(getString(de.lemke.commonutils.R.string.tos))
                    .setMessage(getString(R.string.tos_content))
                    .setPositiveButton(de.lemke.commonutils.R.string.ok) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
                    .create()
                    .show()
                true
            }
            findPreference<PreferenceScreen>("report_bug_pref")!!.onPreferenceClickListener = OnPreferenceClickListener {
                sendEmailBugReport(getString(R.string.email), getString(R.string.app_name))
                true
            }
            findPreference<PreferenceScreen>("delete_app_data_pref")?.setOnPreferenceClickListener { deleteAppDataAndExit() }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            requireView().setBackgroundColor(
                resources.getColor(dev.oneuiproject.oneui.design.R.color.oui_background_color, settingsActivity.theme)
            )
            addRelativeLinksCard(
                RelativeLink(getString(de.lemke.commonutils.R.string.share_app)) { shareApp() },
                RelativeLink(getString(de.lemke.commonutils.R.string.rate_app)) { openApp(settingsActivity.packageName, false) }
            )
        }

        @SuppressLint("WrongConstant", "RestrictedApi")
        override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
            when (preference.key) {
                "dark_mode_pref" -> {
                    val darkMode = newValue as String == "1"
                    AppCompatDelegate.setDefaultNightMode(
                        if (darkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                    )
                    lifecycleScope.launch {
                        updateUserSettings { it.copy(darkMode = darkMode) }
                    }
                    return true
                }

                "dark_mode_auto_pref" -> {
                    val autoDarkMode = newValue as Boolean
                    darkModePref.isEnabled = !autoDarkMode
                    lifecycleScope.launch {
                        if (autoDarkMode) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                        else {
                            if (getUserSettings().darkMode) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                            else AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                        }
                        updateUserSettings { it.copy(autoDarkMode = newValue) }
                    }
                    return true
                }

                "save_location_pref" -> {
                    val saveLocation = SaveLocation.fromStringOrDefault(newValue as String)
                    lifecycleScope.launch {
                        updateUserSettings { it.copy(saveLocation = saveLocation) }
                    }
                    return true
                }
            }
            return false
        }
    }
}