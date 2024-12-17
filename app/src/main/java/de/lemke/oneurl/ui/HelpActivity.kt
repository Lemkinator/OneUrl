package de.lemke.oneurl.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.sendEmailHelp
import de.lemke.commonutils.setCustomBackPressAnimation
import de.lemke.oneurl.R
import de.lemke.oneurl.databinding.ActivityHelpBinding
import de.lemke.oneurl.ui.ProviderActivity.Companion.KEY_INFO_ONLY

@AndroidEntryPoint
class HelpActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHelpBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHelpBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setCustomBackPressAnimation(binding.root)
        binding.providerInfoButton.setOnClickListener {
            startActivity(Intent(this, ProviderActivity::class.java).putExtra(KEY_INFO_ONLY, true))
        }
        binding.contactMeButton.setOnClickListener { sendEmailHelp(getString(R.string.email), getString(R.string.app_name)) }
    }
}