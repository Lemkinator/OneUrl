package de.lemke.oneurl.ui

import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.oneurl.R
import de.lemke.oneurl.databinding.ActivityOobeBinding
import de.lemke.oneurl.domain.UpdateUserSettingsUseCase
import de.lemke.oneurl.domain.utils.TipsItemView
import de.lemke.oneurl.domain.utils.setCustomOnBackPressedLogic
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class OOBEActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOobeBinding
    private lateinit var toSDialog: AlertDialog
    private var time: Long = 0

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out)
        }
        binding = ActivityOobeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setCustomOnBackPressedLogic {
            if (System.currentTimeMillis() - time < 3000) finishAffinity()
            else {
                Toast.makeText(this@OOBEActivity, resources.getString(R.string.press_again_to_exit), Toast.LENGTH_SHORT).show()
                time = System.currentTimeMillis()
            }
        }
        initTipsItems()
        initToSView()
        initFooterButton()
    }

    private fun initTipsItems() {
        val defaultLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        val titles = arrayOf(R.string.oobe_onboard_msg1_title, R.string.oobe_onboard_msg2_title, R.string.oobe_onboard_msg3_title)
        val summaries = arrayOf(R.string.app_description, R.string.oobe_onboard_msg2_summary, R.string.oobe_onboard_msg3_summary)
        val icons = arrayOf(
            dev.oneuiproject.oneui.R.drawable.ic_oui_palette,
            dev.oneuiproject.oneui.R.drawable.ic_oui_credit_card_outline,
            dev.oneuiproject.oneui.R.drawable.ic_oui_decline
        )
        for (i in titles.indices) {
            val item = TipsItemView(this)
            item.setIcon(icons[i])
            item.setTitleText(getString(titles[i]))
            item.setSummaryText(getString(summaries[i]))
            binding.oobeIntroTipsContainer.addView(item, defaultLp)
        }
    }

    private fun initToSView() {
        val tos = getString(R.string.tos)
        val tosText = getString(R.string.oobe_tos_text, tos)
        val tosLink = SpannableString(tosText)
        tosLink.setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: View) {
                    toSDialog.show()
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                }
            },
            tosText.indexOf(tos), tosText.length - if (Locale.getDefault().language == "de") 4 else 1,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        binding.oobeIntroFooterTosText.text = tosLink
        binding.oobeIntroFooterTosText.movementMethod = LinkMovementMethod.getInstance()
        binding.oobeIntroFooterTosText.highlightColor = Color.TRANSPARENT
        initToSDialog()
    }

    private fun initToSDialog() {
        toSDialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.tos))
            .setMessage(getString(R.string.tos_content))
            .setPositiveButton(R.string.ok) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
            .create()
    }

    private fun initFooterButton() {
        if (resources.configuration.screenWidthDp < 360) {
            binding.oobeIntroFooterButton.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
        }
        binding.oobeIntroFooterButton.setOnClickListener {
            binding.oobeIntroFooterTosText.isEnabled = false
            binding.oobeIntroFooterButton.visibility = View.GONE
            binding.oobeIntroFooterButtonProgress.visibility = View.VISIBLE
            lifecycleScope.launch {
                updateUserSettings { it.copy(tosAccepted = true) }
                openNextActivity()
            }
        }
    }

    private fun openNextActivity() {
        startActivity(Intent(this@OOBEActivity, MainActivity::class.java))
        if (Build.VERSION.SDK_INT < 34) {
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        finish()
    }
}