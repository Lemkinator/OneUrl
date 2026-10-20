/*
 * Copyright 2023-2026 Leonard Lemke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.lemke.oneurl.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.CompoundButton
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SeslSeekBar
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle.State.RESUMED
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.ui.utils.BitmapExport
import de.lemke.commonutils.ui.utils.bindColorSwatch
import de.lemke.commonutils.ui.utils.collectState
import de.lemke.commonutils.ui.utils.launchBitmapExport
import de.lemke.commonutils.ui.utils.onSingleLaunchClick
import de.lemke.commonutils.ui.utils.prepareActivityTransformationTo
import de.lemke.commonutils.ui.utils.registerForSingleLaunchResult
import de.lemke.commonutils.ui.utils.setCustomBackAnimation
import de.lemke.commonutils.ui.utils.setWindowTransparent
import de.lemke.commonutils.ui.utils.showColorPickerOnce
import de.lemke.commonutils.ui.utils.singleLaunchMenuItem
import de.lemke.commonutils.ui.utils.toDocumentPick
import de.lemke.oneurl.R
import de.lemke.oneurl.databinding.ActivityGenerateQrCodeBinding
import dev.oneuiproject.oneui.delegates.AppBarAwareYTranslator
import dev.oneuiproject.oneui.delegates.ViewYTranslator
import dev.oneuiproject.oneui.ktx.hideSoftInput
import java.util.Locale
import javax.inject.Inject

private const val MIN_SIZE = 512
private const val MAX_SIZE = 1024

@AndroidEntryPoint
class GenerateQRCodeActivity : AppCompatActivity(), ViewYTranslator by AppBarAwareYTranslator() {
    @Inject
    lateinit var settings: SettingsRepository

    private lateinit var binding: ActivityGenerateQrCodeBinding
    private val viewModel: GenerateQRCodeViewModel by viewModels()
    private var isInitialized = false
    private val exportQRCodeResultLauncher =
        registerForSingleLaunchResult(StartActivityForResult()) { viewModel.onDocumentPicked(it.toDocumentPick()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        prepareActivityTransformationTo()
        super.onCreate(savedInstanceState)
        binding = ActivityGenerateQrCodeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setWindowTransparent(true)
        binding.qrCode.onSingleLaunchClick { viewModel.onCopy() }
        collectState()
        collectState(viewModel.export) { renderExportControls(it) }
        collectState(viewModel.export, minActiveState = RESUMED) { onExport(it) }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_qr, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val enabled = viewModel.export.value != BitmapExport.Running
        menu.findItem(R.id.menu_item_qr_save_as_image).isEnabled = enabled
        menu.findItem(R.id.menu_item_qr_share).isEnabled = enabled
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.menu_item_qr_save_as_image -> {
                singleLaunchMenuItem { viewModel.onSave() }
            }

            R.id.menu_item_qr_share -> {
                singleLaunchMenuItem { viewModel.onShare() }
            }

            else -> {
                super.onOptionsItemSelected(item)
            }
        }

    private fun collectState() =
        collectState(viewModel.state) { state ->
            binding.qrCode.setImageBitmap(state.qrCode)
            binding.colorButtonForeground.bindColorSwatch(state.style.foregroundColor)
            binding.colorButtonBackground.bindColorSwatch(state.style.backgroundColor)
            if (!isInitialized) {
                isInitialized = true
                initControls(state)
                setCustomBackAnimation(binding.root, inAppReview = settings)
                binding.qrCode.translateYWithAppBar(binding.toolbarLayout.appBarLayout, this@GenerateQRCodeActivity)
            }
        }

    private fun onExport(export: BitmapExport) {
        if (export is BitmapExport.Result && launchBitmapExport(export, exportQRCodeResultLauncher)) viewModel.onExportHandled(export)
    }

    private fun renderExportControls(export: BitmapExport) {
        binding.qrCode.isClickable = export != BitmapExport.Running
        invalidateOptionsMenu()
    }

    private fun initControls(initialState: QrUiState) {
        initUrlField(initialState)
        initSizeControls(initialState)
        initToggles(initialState)
        initColorButtons()
    }

    private fun initUrlField(initialState: QrUiState) {
        binding.editTextURL.setText(initialState.url)
        binding.editTextURL.requestFocus()
        binding.editTextURL.text?.let { binding.editTextURL.setSelection(0, it.length) }
        binding.editTextURL.addTextChangedListener { text -> viewModel.setUrl(text.toString()) }
    }

    private fun initSizeControls(initialState: QrUiState) {
        binding.sizeEdittext.setText(String.format(Locale.ROOT, "%d", initialState.style.size))
        binding.sizeEdittext.setOnEditorActionListener { textView, _, _ ->
            val newSize = textView.text.toString().toIntOrNull()
            if (newSize != null) {
                val clamped = newSize.coerceAtLeast(MIN_SIZE).coerceAtMost(MAX_SIZE)
                binding.sizeSeekbar.progress = clamped
                viewModel.setSize(clamped)
            }
            hideSoftInput()
            textView.clearFocus()
            true
        }
        binding.sizeSeekbar.max = MAX_SIZE
        binding.sizeSeekbar.min = MIN_SIZE
        binding.sizeSeekbar.progress = initialState.style.size
        binding.sizeSeekbar.setOnSeekBarChangeListener(
            object : SeslSeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeslSeekBar,
                    progress: Int,
                    fromUser: Boolean,
                ) {
                    binding.sizeEdittext.setText(String.format(Locale.ROOT, "%d", progress))
                    viewModel.setSize(progress)
                }

                override fun onStartTrackingTouch(seekBar: SeslSeekBar) {
                    // no-op
                }

                override fun onStopTrackingTouch(seekBar: SeslSeekBar) {
                    // no-op
                }
            },
        )
    }

    private fun initToggles(initialState: QrUiState) {
        binding.frameCheckbox.isChecked = initialState.style.roundedFrame
        binding.frameCheckbox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean -> viewModel.setRoundedFrame(isChecked) }

        binding.iconCheckbox.isChecked = initialState.style.icon
        binding.iconCheckbox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean -> viewModel.setIcon(isChecked) }

        binding.tintBorderCheckbox.isChecked = initialState.style.tintBorder
        binding.tintBorderCheckbox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            viewModel.setTintBorder(isChecked)
        }

        binding.tintAnchorCheckbox.isChecked = initialState.style.tintAnchor
        binding.tintAnchorCheckbox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            viewModel.setTintAnchor(isChecked)
        }
    }

    private fun initColorButtons() {
        binding.colorButtonBackground.onSingleLaunchClick {
            val state = viewModel.state.value
            showColorPickerOnce(
                BACKGROUND_COLOR_PICKER_TAG,
                state.style.backgroundColor,
                state.recentBackgroundColors,
                viewModel::setBackgroundColor,
            )
        }
        binding.colorButtonForeground.onSingleLaunchClick {
            val state = viewModel.state.value
            showColorPickerOnce(
                FOREGROUND_COLOR_PICKER_TAG,
                state.style.foregroundColor,
                state.recentForegroundColors,
                viewModel::setForegroundColor,
            )
        }
    }

    companion object {
        private const val BACKGROUND_COLOR_PICKER_TAG = "backgroundColorPicker"
        private const val FOREGROUND_COLOR_PICKER_TAG = "foregroundColorPicker"
    }
}
