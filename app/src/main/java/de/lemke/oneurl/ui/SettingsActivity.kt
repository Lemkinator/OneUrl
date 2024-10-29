package de.lemke.oneurl.ui

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.preference.DropDownPreference
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceClickListener
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.oneurl.R
import de.lemke.oneurl.data.SaveLocation
import de.lemke.oneurl.databinding.ActivitySettingsBinding
import de.lemke.oneurl.domain.GetUserSettingsUseCase
import de.lemke.oneurl.domain.OpenLinkUseCase
import de.lemke.oneurl.domain.UpdateUserSettingsUseCase
import dev.oneuiproject.oneui.preference.HorizontalRadioPreference
import dev.oneuiproject.oneui.preference.internal.PreferenceRelatedCard
import dev.oneuiproject.oneui.utils.PreferenceUtils.createRelatedCard
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbarLayout.setNavigationButtonTooltip(getString(R.string.sesl_navigate_up))
        binding.toolbarLayout.setNavigationButtonOnClickListener { finishAfterTransition() }
        if (savedInstanceState == null) supportFragmentManager.beginTransaction().replace(R.id.settings, SettingsFragment()).commit()
    }

    @AndroidEntryPoint
    class SettingsFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener {
        private lateinit var settingsActivity: SettingsActivity
        private lateinit var darkModePref: HorizontalRadioPreference
        private lateinit var autoDarkModePref: SwitchPreferenceCompat
        private lateinit var saveLocationPref: DropDownPreference
        private var relatedCard: PreferenceRelatedCard? = null

        @Inject
        lateinit var openLink: OpenLinkUseCase

        @Inject
        lateinit var getUserSettings: GetUserSettingsUseCase

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
            AppCompatDelegate.getDefaultNightMode()
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
                    val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:${settingsActivity.packageName}"))
                    try {
                        startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        e.printStackTrace()
                        Toast.makeText(settingsActivity, getString(R.string.change_language_not_supported_by_device), Toast.LENGTH_SHORT).show()
                    }
                    true
                }
            }

            lifecycleScope.launch {
                val userSettings = getUserSettings()
                autoDarkModePref.isChecked = userSettings.autoDarkMode
                darkModePref.isEnabled = !autoDarkModePref.isChecked
                darkModePref.value = if (userSettings.darkMode) "1" else "0"
                saveLocationPref.entries = SaveLocation.entries.map { it.toLocalizedString(requireContext()) }.toTypedArray()
                saveLocationPref.entryValues = SaveLocation.entries.map { it.name }.toTypedArray()
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                    saveLocationPref.value = SaveLocation.CUSTOM.name
                    saveLocationPref.isEnabled = false
                } else {
                    saveLocationPref.value = userSettings.saveLocation.name
                }
            }

            findPreference<PreferenceScreen>("privacy_pref")!!.onPreferenceClickListener = OnPreferenceClickListener {
                openLink(getString(R.string.privacy_website))
                true
            }

            findPreference<PreferenceScreen>("tos_pref")!!.onPreferenceClickListener = OnPreferenceClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.tos))
                    .setMessage(getString(R.string.tos_content))
                    .setPositiveButton(R.string.ok) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
                    .create()
                    .show()
                true
            }
            findPreference<PreferenceScreen>("report_bug_pref")!!.onPreferenceClickListener = OnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_SENDTO)
                intent.data = Uri.parse("mailto:") // only email apps should handle this
                intent.putExtra(Intent.EXTRA_EMAIL, arrayOf(getString(R.string.email)))
                intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
                intent.putExtra(Intent.EXTRA_TEXT, "")
                try {
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                    Toast.makeText(requireContext(), getString(R.string.no_email_app_installed), Toast.LENGTH_SHORT).show()
                }
                true
            }

            AppUpdateManagerFactory.create(requireContext()).appUpdateInfo.addOnSuccessListener { appUpdateInfo: AppUpdateInfo ->
                findPreference<Preference>("about_app_pref")?.widgetLayoutResource =
                    if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) R.layout.sesl_preference_badge else 0
            }
            findPreference<PreferenceScreen>("delete_app_data_pref")?.setOnPreferenceClickListener {
                AlertDialog.Builder(settingsActivity)
                    .setTitle(R.string.delete_appdata_and_exit)
                    .setMessage(R.string.delete_appdata_and_exit_warning)
                    .setNegativeButton(R.string.sesl_cancel, null)
                    .setPositiveButton(R.string.ok) { _: DialogInterface, _: Int ->
                        (settingsActivity.getSystemService(ACTIVITY_SERVICE) as ActivityManager).clearApplicationUserData()
                    }
                    .create()
                    .show()
                true
            }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            requireView().setBackgroundColor(
                resources.getColor(dev.oneuiproject.oneui.design.R.color.oui_background_color, settingsActivity.theme)
            )
        }

        override fun onStart() {
            super.onStart()
            lifecycleScope.launch {
                findPreference<PreferenceCategory>("dev_options")?.isVisible = getUserSettings().devModeEnabled
            }
            setRelatedCardView()
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

        private fun setRelatedCardView() {
            if (relatedCard == null) {
                relatedCard = createRelatedCard(settingsActivity)
                relatedCard?.setTitleText(getString(dev.oneuiproject.oneui.design.R.string.oui_relative_description))
                relatedCard?.addButton(getString(R.string.help)) { startActivity(Intent(settingsActivity, HelpActivity::class.java)) }
                    ?.addButton(getString(R.string.about_me)) { startActivity(Intent(settingsActivity, AboutMeActivity::class.java)) }
                    ?.show(this)
            }
        }
    }
}