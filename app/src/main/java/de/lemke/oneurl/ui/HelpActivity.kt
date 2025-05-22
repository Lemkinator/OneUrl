package de.lemke.oneurl.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.prepareActivityTransformationBetween
import de.lemke.commonutils.sendEmailHelp
import de.lemke.commonutils.setCustomBackAnimation
import de.lemke.commonutils.transformToActivity
import de.lemke.oneurl.R
import de.lemke.oneurl.databinding.ActivityHelpBinding
import dev.oneuiproject.oneui.ktx.onSingleClick

@AndroidEntryPoint
class HelpActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHelpBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        prepareActivityTransformationBetween()
        super.onCreate(savedInstanceState)
        binding = ActivityHelpBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setCustomBackAnimation(binding.root)
        binding.providerInfoButton.apply { onSingleClick { transformToActivity(ProviderActivity::class.java, "ProviderTransformation") } }
        binding.contactMeButton.setOnClickListener { sendEmailHelp(getString(R.string.email), getString(R.string.app_name)) }
    }
}