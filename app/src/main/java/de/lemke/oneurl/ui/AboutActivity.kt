package de.lemke.oneurl.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.IntentSender.SendIntentException
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.tasks.Task
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.oneurl.BuildConfig
import de.lemke.oneurl.R
import de.lemke.oneurl.databinding.ActivityAboutBinding
import de.lemke.oneurl.domain.GetUserSettingsUseCase
import de.lemke.oneurl.domain.OpenAppUseCase
import de.lemke.oneurl.domain.UpdateUserSettingsUseCase
import dev.oneuiproject.oneui.layout.AppInfoLayout.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AboutActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAboutBinding
    private lateinit var appUpdateManager: AppUpdateManager
    private lateinit var appUpdateInfo: AppUpdateInfo
    private lateinit var appUpdateInfoTask: Task<AppUpdateInfo>
    private var clicks = 0

    @Inject
    lateinit var openApp: OpenAppUseCase

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        appUpdateManager = AppUpdateManagerFactory.create(this)
        binding.appInfoLayout.addOptionalText(getString(R.string.app_description))
        binding.appInfoLayout.status = LOADING
        //status: LOADING NO_UPDATE UPDATE_AVAILABLE NOT_UPDATEABLE NO_CONNECTION
        binding.appInfoLayout.setMainButtonClickListener(object : OnClickListener {
            override fun onUpdateClicked(v: View?) {
                startUpdateFlow()
            }

            override fun onRetryClicked(v: View?) {
                binding.appInfoLayout.status = LOADING
                checkUpdate()
            }
        })
        val version: TextView = binding.appInfoLayout.findViewById(dev.oneuiproject.oneui.design.R.id.app_info_version)
        lifecycleScope. launch { setVersionTextView(version, getUserSettings().devModeEnabled) }
        version.setOnClickListener {
            clicks++
            if (clicks > 5) {
                clicks = 0
                lifecycleScope.launch {
                    val newDevModeEnabled = !getUserSettings().devModeEnabled
                    updateUserSettings { it.copy(devModeEnabled = newDevModeEnabled) }
                    setVersionTextView(version, newDevModeEnabled)
                }
            }
        }
        binding.aboutBtnOpenInStore.setOnClickListener { openApp(packageName, false) }
        binding.aboutBtnOpenOneuiGithub.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.oneui_github_link))))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, getString(R.string.no_browser_app_installed), Toast.LENGTH_SHORT).show()
            }
        }
        binding.aboutBtnAboutMe.setOnClickListener {
            startActivity(Intent(this@AboutActivity, AboutMeActivity::class.java))
        }
        checkUpdate()
    }

    private fun setVersionTextView(textView: TextView, devModeEnabled: Boolean) {
        lifecycleScope.launch {
            textView.text = getString(
                dev.oneuiproject.oneui.design.R.string.version_info, BuildConfig.VERSION_NAME + if (devModeEnabled) " (dev)" else ""
            )
        }
    }

    // Checks that the update is not stalled during 'onResume()'.
    // However, you should execute this check at all entry points into the app.
    override fun onResume() {
        super.onResume()
        appUpdateManager
            .appUpdateInfo
            .addOnSuccessListener { appUpdateInfo ->
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    // If an in-app update is already running, resume the update.
                    startUpdateFlow()
                }
            }
    }

    private fun checkUpdate() {
        val connectivityManager = getSystemService(ConnectivityManager::class.java)
        val caps = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            binding.appInfoLayout.status = NO_CONNECTION
            return
        }

        // Returns an intent object that you use to check for an update.
        appUpdateInfoTask = appUpdateManager.appUpdateInfo
        // Checks that the platform will allow the specified type of update.
        appUpdateInfoTask
            .addOnSuccessListener { appUpdateInfo: AppUpdateInfo ->
                this.appUpdateInfo = appUpdateInfo
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                    binding.appInfoLayout.status = UPDATE_AVAILABLE
                }
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_NOT_AVAILABLE) {
                    binding.appInfoLayout.status = NO_UPDATE
                }
            }
            .addOnFailureListener { appUpdateInfo: Exception ->
                Log.w("AboutActivity", appUpdateInfo.message.toString())
                binding.appInfoLayout.status = NOT_UPDATEABLE
            }
    }

    private fun startUpdateFlow() {
        try {
            appUpdateManager.startUpdateFlowForResult( // Pass the intent that is returned by 'getAppUpdateInfo()'.
                appUpdateInfo,  //AppUpdateType.FLEXIBLE,
                AppUpdateType.IMMEDIATE,
                this,
                UPDATEREQUESTCODE
            )
        } catch (e: SendIntentException) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val UPDATEREQUESTCODE = 5
    }
}