package de.lemke.oneurl.ui

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color.BLACK
import android.graphics.Color.WHITE
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.CompoundButton
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SeslSeekBar
import androidx.core.graphics.toColor
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.picker3.app.SeslColorPickerDialog
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.copyToClipboard
import de.lemke.commonutils.data.commonUtilsSettings
import de.lemke.commonutils.exportBitmap
import de.lemke.commonutils.prepareActivityTransformationTo
import de.lemke.commonutils.saveBitmapToUri
import de.lemke.commonutils.setCustomBackAnimation
import de.lemke.commonutils.setWindowTransparent
import de.lemke.commonutils.share
import de.lemke.oneurl.R
import de.lemke.oneurl.databinding.ActivityGenerateQrCodeBinding
import de.lemke.oneurl.domain.GetUserSettingsUseCase
import de.lemke.oneurl.domain.UpdateUserSettingsUseCase
import dev.oneuiproject.oneui.delegates.AppBarAwareYTranslator
import dev.oneuiproject.oneui.delegates.ViewYTranslator
import dev.oneuiproject.oneui.ktx.hideSoftInput
import dev.oneuiproject.oneui.qr.utils.QrEncoder
import kotlinx.coroutines.launch
import javax.inject.Inject
import de.lemke.commonutils.R as commonutilsR

@AndroidEntryPoint
class GenerateQRCodeActivity : AppCompatActivity(), ViewYTranslator by AppBarAwareYTranslator() {
    private lateinit var binding: ActivityGenerateQrCodeBinding
    private lateinit var url: String
    private var qrCode: Bitmap? = null
    private var backgroundColor = 0
    private var foregroundColor = 0
    private var tintAnchor = false
    private var tintBorder = false
    private var size = 0
    private var roundedFrame = false
    private var icon = false
    private val minSize = 512
    private val maxSize = 1024
    private val exportQRCodeResultLauncher = registerForActivityResult(StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) saveBitmapToUri(it.data?.data, qrCode)
    }

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        prepareActivityTransformationTo()
        super.onCreate(savedInstanceState)
        binding = ActivityGenerateQrCodeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setWindowTransparent(true)
        lifecycleScope.launch {
            val userSettings = getUserSettings()
            url = userSettings.qrURL
            size = userSettings.qrSize
            roundedFrame = userSettings.qrFrame
            icon = userSettings.qrIcon
            tintBorder = userSettings.qrTintBorder
            tintAnchor = userSettings.qrTintAnchor
            backgroundColor = userSettings.qrRecentBackgroundColors.first()
            foregroundColor = userSettings.qrRecentForegroundColors.first()
            initViews()
            setCustomBackAnimation(binding.root, showInAppReviewIfPossible = true)
            binding.qrCode.translateYWithAppBar(binding.toolbarLayout.appBarLayout, this@GenerateQRCodeActivity)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_qr, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_item_qr_save_as_image -> {
                qrCode?.let { exportBitmap(commonUtilsSettings.imageSaveLocation, it, url, exportQRCodeResultLauncher) }
                return true
            }

            R.id.menu_item_qr_share -> {
                qrCode?.share(this, "QRCode.png")
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun initViews() {
        generateQRCode()
        binding.qrCode.setOnClickListener { qrCode?.copyToClipboard(this, "QR Code", "QRCode.png") == true }
        binding.editTextURL.setText(url)
        binding.editTextURL.requestFocus()
        binding.editTextURL.text?.let { binding.editTextURL.setSelection(0, it.length) }
        binding.editTextURL.addTextChangedListener { text ->
            url = text.toString()
            generateQRCode()
            lifecycleScope.launch { updateUserSettings { it.copy(qrURL = text.toString()) } }
        }
        initSize()
        binding.frameCheckbox.isChecked = roundedFrame
        binding.frameCheckbox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            roundedFrame = isChecked
            generateQRCode()
            lifecycleScope.launch { updateUserSettings { it.copy(qrFrame = isChecked) } }
        }
        binding.iconCheckbox.isChecked = icon
        binding.iconCheckbox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            icon = isChecked
            generateQRCode()
            lifecycleScope.launch { updateUserSettings { it.copy(qrIcon = isChecked) } }
        }
        binding.tintBorderCheckbox.isChecked = tintBorder
        binding.tintBorderCheckbox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            tintBorder = isChecked
            generateQRCode()
            lifecycleScope.launch { updateUserSettings { it.copy(qrTintBorder = isChecked) } }
        }
        binding.tintAnchorCheckbox.isChecked = tintAnchor
        binding.tintAnchorCheckbox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            tintAnchor = isChecked
            generateQRCode()
            lifecycleScope.launch { updateUserSettings { it.copy(qrTintAnchor = isChecked) } }
        }
        initColors()
    }

    @SuppressLint("SetTextI18n")
    private fun initSize() {
        binding.sizeEdittext.setText(size.toString())
        binding.sizeEdittext.setOnEditorActionListener { textView, _, _ ->
            val newSize = textView.text.toString().toIntOrNull()
            if (newSize != null) {
                size = newSize.coerceAtLeast(minSize).coerceAtMost(maxSize)
                binding.sizeSeekbar.progress = size
                generateQRCode()
                lifecycleScope.launch { updateUserSettings { it.copy(qrSize = size) } }
            }
            hideSoftInput()
            textView.clearFocus()
            true
        }
        binding.sizeSeekbar.max = maxSize
        binding.sizeSeekbar.min = minSize
        binding.sizeSeekbar.progress = size
        binding.sizeSeekbar.setOnSeekBarChangeListener(object : SeslSeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeslSeekBar, progress: Int, fromUser: Boolean) {
                size = progress
                binding.sizeEdittext.setText(size.toString())
                generateQRCode()
                lifecycleScope.launch { updateUserSettings { it.copy(qrSize = size) } }
            }

            override fun onStartTrackingTouch(seekBar: SeslSeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeslSeekBar) {}
        })
    }

    private fun initColors() {
        setButtonColors()
        binding.colorButtonBackground.setOnClickListener {
            lifecycleScope.launch {
                val userSettings = getUserSettings()
                SeslColorPickerDialog(
                    this@GenerateQRCodeActivity,
                    { color: Int ->
                        backgroundColor = color
                        generateQRCode()
                        val recentColors = (listOf(color) + userSettings.qrRecentBackgroundColors).distinct().take(6)
                        lifecycleScope.launch { updateUserSettings { it.copy(qrRecentBackgroundColors = recentColors) } }
                        setButtonColors()
                    },
                    userSettings.qrRecentBackgroundColors.first(), userSettings.qrRecentBackgroundColors.toIntArray(), true
                ).apply {
                    setTransparencyControlEnabled(true)
                    show()
                }
            }
        }
        binding.colorButtonForeground.setOnClickListener {
            lifecycleScope.launch {
                val userSettings = getUserSettings()
                SeslColorPickerDialog(
                    this@GenerateQRCodeActivity,
                    { color: Int ->
                        foregroundColor = color
                        generateQRCode()
                        val recentColors = (listOf(color) + userSettings.qrRecentForegroundColors).distinct().take(6)
                        lifecycleScope.launch { updateUserSettings { it.copy(qrRecentForegroundColors = recentColors) } }
                        setButtonColors()
                    },
                    userSettings.qrRecentForegroundColors.first(), userSettings.qrRecentForegroundColors.toIntArray(), true
                ).apply {
                    setTransparencyControlEnabled(true)
                    show()
                }
            }
        }
    }

    private fun setButtonColors() {
        binding.colorButtonBackground.backgroundTintList = ColorStateList.valueOf(backgroundColor)
        binding.colorButtonForeground.backgroundTintList = ColorStateList.valueOf(foregroundColor)
        binding.colorButtonBackground.setTextColor(if (backgroundColor.toColor().luminance() >= 0.5) BLACK else WHITE)
        binding.colorButtonForeground.setTextColor(if (foregroundColor.toColor().luminance() >= 0.5) BLACK else WHITE)
    }

    private fun generateQRCode() {
        qrCode = with(QrEncoder(this, url)) {
            if (icon) setIcon(commonutilsR.drawable.ic_launcher_themed)
            setBackgroundColor(backgroundColor)
            setForegroundColor(foregroundColor, tintAnchor, tintBorder)
            setSize(size)
            roundedFrame(roundedFrame)
            generate()
        }
        binding.qrCode.setImageBitmap(qrCode)
    }
}