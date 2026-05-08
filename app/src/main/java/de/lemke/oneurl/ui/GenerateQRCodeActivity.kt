package de.lemke.oneurl.ui

import android.content.res.ColorStateList
import android.graphics.Color.BLACK
import android.graphics.Color.WHITE
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.CompoundButton
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SeslSeekBar
import androidx.core.graphics.toColor
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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
import dev.oneuiproject.oneui.delegates.AppBarAwareYTranslator
import dev.oneuiproject.oneui.delegates.ViewYTranslator
import dev.oneuiproject.oneui.ktx.hideSoftInput
import kotlinx.coroutines.launch
import de.lemke.commonutils.R as commonutilsR

@AndroidEntryPoint
class GenerateQRCodeActivity : AppCompatActivity(), ViewYTranslator by AppBarAwareYTranslator() {
    private lateinit var binding: ActivityGenerateQrCodeBinding
    private val viewModel: GenerateQRCodeViewModel by viewModels()
    private val minSize = 512
    private val maxSize = 1024
    private var isInitialized = false
    private val exportQRCodeResultLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val state = viewModel.state.value
            saveBitmapToUri(result.data?.data, state.qrCode)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prepareActivityTransformationTo()
        super.onCreate(savedInstanceState)
        binding = ActivityGenerateQrCodeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setWindowTransparent(true)
        collectState()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_qr, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val state = viewModel.state.value
        when (item.itemId) {
            R.id.menu_item_qr_save_as_image -> {
                state.qrCode?.let { exportBitmap(commonUtilsSettings.imageSaveLocation, it, state.url, exportQRCodeResultLauncher) }
                return true
            }
            R.id.menu_item_qr_share -> {
                state.qrCode?.share(this, "QRCode.png")
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun collectState() = lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.state.collect { state ->
                if (state.isLoading) return@collect
                state.qrCode?.let { binding.qrCode.setImageBitmap(it) }
                binding.qrCode.setOnClickListener { state.qrCode?.copyToClipboard(this@GenerateQRCodeActivity, "QR Code", "QRCode.png") }
                updateButtonColors(state.foregroundColor, state.backgroundColor)
                if (!isInitialized) {
                    isInitialized = true
                    initControls(state)
                    setCustomBackAnimation(binding.root, showInAppReviewIfPossible = true)
                    binding.qrCode.translateYWithAppBar(binding.toolbarLayout.appBarLayout, this@GenerateQRCodeActivity)
                }
            }
        }
    }

    private fun initControls(initialState: QrUiState) {
        binding.editTextURL.setText(initialState.url)
        binding.editTextURL.requestFocus()
        binding.editTextURL.text?.let { binding.editTextURL.setSelection(0, it.length) }
        binding.editTextURL.addTextChangedListener { text -> viewModel.setUrl(text.toString()) }

        binding.sizeEdittext.setText(initialState.size.toString())
        binding.sizeEdittext.setOnEditorActionListener { textView, _, _ ->
            val newSize = textView.text.toString().toIntOrNull()
            if (newSize != null) {
                val clamped = newSize.coerceAtLeast(minSize).coerceAtMost(maxSize)
                binding.sizeSeekbar.progress = clamped
                viewModel.setSize(clamped)
            }
            hideSoftInput()
            textView.clearFocus()
            true
        }
        binding.sizeSeekbar.max = maxSize
        binding.sizeSeekbar.min = minSize
        binding.sizeSeekbar.progress = initialState.size
        binding.sizeSeekbar.setOnSeekBarChangeListener(object : SeslSeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeslSeekBar, progress: Int, fromUser: Boolean) {
                binding.sizeEdittext.setText(progress.toString())
                viewModel.setSize(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeslSeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeslSeekBar) {}
        })

        binding.frameCheckbox.isChecked = initialState.roundedFrame
        binding.frameCheckbox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean -> viewModel.setRoundedFrame(isChecked) }

        binding.iconCheckbox.isChecked = initialState.icon
        binding.iconCheckbox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean -> viewModel.setIcon(isChecked) }

        binding.tintBorderCheckbox.isChecked = initialState.tintBorder
        binding.tintBorderCheckbox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean -> viewModel.setTintBorder(isChecked) }

        binding.tintAnchorCheckbox.isChecked = initialState.tintAnchor
        binding.tintAnchorCheckbox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean -> viewModel.setTintAnchor(isChecked) }

        binding.colorButtonBackground.setOnClickListener {
            val state = viewModel.state.value
            SeslColorPickerDialog(
                this,
                { color: Int -> viewModel.setBackgroundColor(color) },
                state.backgroundColor,
                state.recentBackgroundColors.toIntArray(),
                true
            ).apply {
                setTransparencyControlEnabled(true)
                show()
            }
        }
        binding.colorButtonForeground.setOnClickListener {
            val state = viewModel.state.value
            SeslColorPickerDialog(
                this,
                { color: Int -> viewModel.setForegroundColor(color) },
                state.foregroundColor,
                state.recentForegroundColors.toIntArray(),
                true
            ).apply {
                setTransparencyControlEnabled(true)
                show()
            }
        }
    }

    private fun updateButtonColors(foregroundColor: Int, backgroundColor: Int) {
        binding.colorButtonBackground.backgroundTintList = ColorStateList.valueOf(backgroundColor)
        binding.colorButtonForeground.backgroundTintList = ColorStateList.valueOf(foregroundColor)
        binding.colorButtonBackground.setTextColor(if (backgroundColor.toColor().luminance() >= 0.5) BLACK else WHITE)
        binding.colorButtonForeground.setTextColor(if (foregroundColor.toColor().luminance() >= 0.5) BLACK else WHITE)
    }
}
