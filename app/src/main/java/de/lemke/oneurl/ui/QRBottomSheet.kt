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

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle.State.RESUMED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.ui.utils.collectState
import de.lemke.commonutils.ui.utils.isSamsungQuickShareAvailable
import de.lemke.commonutils.ui.utils.onSingleLaunchClick
import de.lemke.commonutils.ui.utils.registerForSingleLaunchResult
import de.lemke.oneurl.databinding.ViewQrBottomsheetBinding
import dev.oneuiproject.oneui.app.SemBottomSheetDialogFragment

@AndroidEntryPoint
class QRBottomSheet : SemBottomSheetDialogFragment() {
    private lateinit var binding: ViewQrBottomsheetBinding
    private val viewModel: QRBottomSheetViewModel by viewModels()
    private val exportQRCodeResultLauncher: ActivityResultLauncher<Intent> =
        registerForSingleLaunchResult(StartActivityForResult()) { viewModel.onDocumentPicked(it.toDocumentPick()) }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        (super.onCreateDialog(savedInstanceState) as BottomSheetDialog).apply {
            behavior.skipCollapsed = true
            setOnShowListener { behavior.state = STATE_EXPANDED }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ViewQrBottomsheetBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        if (requireContext().isSamsungQuickShareAvailable()) {
            binding.quickShareButton.isVisible = true
        }
        binding.quickShareButton.onSingleLaunchClick { viewModel.onQuickShare() }
        binding.shareButton.onSingleLaunchClick { viewModel.onShare() }
        binding.saveButton.onSingleLaunchClick { viewModel.onSave() }
        collectState(viewModel.state) { render(it) }
        collectState(viewModel.export) { renderExportControls() }
        collectState(viewModel.export, minActiveState = RESUMED) { onExport(it) }
    }

    private fun render(state: QRBottomSheetUiState) {
        binding.title.text = state.shortURL
        binding.qrCode.setImageBitmap(state.qrCode)
        renderExportControls()
    }

    private fun renderExportControls() {
        val enabled = viewModel.state.value.qrCode != null && viewModel.export.value != QRCodeExport.Running
        binding.quickShareButton.isEnabled = enabled
        binding.shareButton.isEnabled = enabled
        binding.saveButton.isEnabled = enabled
    }

    private fun onExport(export: QRCodeExport) {
        if (export is QRCodeExport.Result && requireContext().launchQRCodeExport(export, exportQRCodeResultLauncher)) {
            viewModel.onExportHandled(export)
        }
    }

    companion object {
        const val KEY_SHORT_URL = "key_short_url"

        fun createQRBottomSheet(shortURL: String): QRBottomSheet =
            QRBottomSheet().apply {
                arguments =
                    Bundle().apply { putString(KEY_SHORT_URL, shortURL) }
            }
    }
}
