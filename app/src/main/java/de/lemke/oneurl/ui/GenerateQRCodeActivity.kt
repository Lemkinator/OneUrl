package de.lemke.oneurl.ui

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import android.widget.CompoundButton
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SeslSeekBar
import androidx.core.graphics.toColor
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.picker3.app.SeslColorPickerDialog
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.oneurl.R
import de.lemke.oneurl.data.SaveLocation
import de.lemke.oneurl.databinding.ActivityGenerateQrCodeBinding
import de.lemke.oneurl.domain.GetUserSettingsUseCase
import de.lemke.oneurl.domain.ShowInAppReviewOrFinishUseCase
import de.lemke.oneurl.domain.UpdateUserSettingsUseCase
import de.lemke.oneurl.domain.qr.CopyQRCodeUseCase
import de.lemke.oneurl.domain.qr.ExportQRCodeToSaveLocationUseCase
import de.lemke.oneurl.domain.qr.ExportQRCodeUseCase
import de.lemke.oneurl.domain.qr.ShareQRCodeUseCase
import de.lemke.oneurl.domain.utils.setCustomOnBackPressedLogic
import dev.oneuiproject.oneui.qr.QREncoder
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class GenerateQRCodeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGenerateQrCodeBinding
    private lateinit var pickExportFolderActivityResultLauncher: ActivityResultLauncher<Uri?>
    private lateinit var url: String
    private lateinit var qrCode: Bitmap
    private lateinit var saveLocation: SaveLocation
    private var backgroundColor = 0
    private var foregroundColor = 0
    private var tintAnchor = false
    private var tintBorder = false
    private var size = 0
    private var frame = false
    private var icon = false

    private val minSize = 512
    private val maxSize = 1024

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    @Inject
    lateinit var exportQRCode: ExportQRCodeUseCase

    @Inject
    lateinit var exportQRCodeToSaveLocation: ExportQRCodeToSaveLocationUseCase

    @Inject
    lateinit var copyQRCode: CopyQRCodeUseCase

    @Inject
    lateinit var shareQRCode: ShareQRCodeUseCase

    @Inject
    lateinit var showInAppReviewOrFinish: ShowInAppReviewOrFinishUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGenerateQrCodeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        pickExportFolderActivityResultLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (this::qrCode.isInitialized && this::url.isInitialized) exportQRCode(uri, qrCode, url)
        }
        binding.toolbarLayout.setNavigationButtonOnClickListener { lifecycleScope.launch { showInAppReviewOrFinish(this@GenerateQRCodeActivity) } }
        binding.toolbarLayout.setNavigationButtonTooltip(getString(R.string.sesl_navigate_up))
        lifecycleScope.launch {
            val userSettings = getUserSettings()
            url = userSettings.qrURL
            size = userSettings.qrSize
            frame = userSettings.qrFrame
            icon = userSettings.qrIcon
            tintBorder = userSettings.qrTintBorder
            tintAnchor = userSettings.qrTintAnchor
            backgroundColor = userSettings.qrRecentBackgroundColors.first()
            foregroundColor = userSettings.qrRecentForegroundColors.first()
            saveLocation = userSettings.saveLocation
            initViews()
            if (showInAppReviewOrFinish.canShowInAppReview()) {
                setCustomOnBackPressedLogic { lifecycleScope.launch { showInAppReviewOrFinish(this@GenerateQRCodeActivity) } }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_qr, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_item_qr_save_as_image -> {
                if (saveLocation == SaveLocation.CUSTOM) {
                    pickExportFolderActivityResultLauncher.launch(Uri.fromFile(File(Environment.getExternalStorageDirectory().absolutePath)))
                } else {
                    exportQRCodeToSaveLocation(saveLocation, qrCode, url)
                }
                return true
            }

            R.id.menu_item_qr_share -> {
                shareQRCode(qrCode)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun initViews() {
        generateQRCode()
        binding.qrCode.setOnClickListener { copyQRCode(qrCode) }
        binding.editTextURL.setText(url)
        binding.editTextURL.requestFocus()
        binding.editTextURL.text?.let { binding.editTextURL.setSelection(0, it.length) }
        binding.editTextURL.addTextChangedListener { text ->
            url = text.toString()
            generateQRCode()
            lifecycleScope.launch { updateUserSettings { it.copy(qrURL = text.toString()) } }
        }
        initSize()
        binding.frameCheckbox.isChecked = frame
        binding.frameCheckbox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            frame = isChecked
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
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(
                textView.windowToken,
                InputMethodManager.HIDE_NOT_ALWAYS
            )
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
                val userSettingsColor = getUserSettings()
                val dialog = SeslColorPickerDialog(
                    this@GenerateQRCodeActivity,
                    { color: Int ->
                        backgroundColor = color
                        generateQRCode()
                        val recentColors = userSettingsColor.qrRecentBackgroundColors.toMutableList()
                        if (recentColors.size >= 6) recentColors.removeAt(5)
                        recentColors.add(0, color)
                        lifecycleScope.launch { updateUserSettings { it.copy(qrRecentBackgroundColors = recentColors) } }
                        setButtonColors()
                    },
                    userSettingsColor.qrRecentBackgroundColors.first(), buildIntArray(userSettingsColor.qrRecentBackgroundColors), true
                )
                dialog.setTransparencyControlEnabled(true)
                dialog.show()
            }
        }
        binding.colorButtonForeground.setOnClickListener {
            lifecycleScope.launch {
                val userSettingsColor = getUserSettings()
                val dialog = SeslColorPickerDialog(
                    this@GenerateQRCodeActivity,
                    { color: Int ->
                        foregroundColor = color
                        generateQRCode()
                        val recentColors = userSettingsColor.qrRecentForegroundColors.toMutableList()
                        if (recentColors.size >= 6) recentColors.removeAt(5)
                        recentColors.add(0, color)
                        lifecycleScope.launch { updateUserSettings { it.copy(qrRecentForegroundColors = recentColors) } }
                        setButtonColors()
                    },
                    userSettingsColor.qrRecentForegroundColors.first(), buildIntArray(userSettingsColor.qrRecentForegroundColors), true
                )
                dialog.setTransparencyControlEnabled(true)
                dialog.show()
            }
        }
    }

    private fun setButtonColors() {
        binding.colorButtonBackground.backgroundTintList = ColorStateList.valueOf(backgroundColor)
        binding.colorButtonForeground.backgroundTintList = ColorStateList.valueOf(foregroundColor)
        binding.colorButtonBackground.setTextColor(if (backgroundColor.toColor().luminance() >= 0.5) Color.BLACK else Color.WHITE)
        binding.colorButtonForeground.setTextColor(if (foregroundColor.toColor().luminance() >= 0.5) Color.BLACK else Color.WHITE)
    }

    private fun buildIntArray(integers: List<Int>): IntArray {
        val ints = IntArray(integers.size)
        var i = 0
        for (n in integers) {
            ints[i++] = n
        }
        return ints
    }

    private fun generateQRCode() {
        qrCode = with(QREncoder(this, url)) {
            if (icon) setIcon(R.drawable.ic_launcher_themed)
            setBGColor(backgroundColor)
            setFGColor(foregroundColor, tintAnchor, tintBorder)
            setSize(size)
            setFrame(frame)
            generate()
        }
        binding.qrCode.setImageBitmap(qrCode)
    }
}