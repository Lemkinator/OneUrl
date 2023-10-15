package de.lemke.oneurl.ui

import android.graphics.Bitmap
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.oneurl.R
import de.lemke.oneurl.databinding.ActivityGenerateQrCodeBinding
import de.lemke.oneurl.domain.GetUserSettingsUseCase
import de.lemke.oneurl.domain.UpdateUserSettingsUseCase
import dev.oneuiproject.oneui.qr.QREncoder
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class GenerateQRCodeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGenerateQrCodeBinding
    private var qrCode: Bitmap? = null

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGenerateQrCodeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbarLayout.setNavigationButtonOnClickListener { finish() }
        binding.toolbarLayout.tooltipText = getString(R.string.sesl_navigate_up)
        lifecycleScope.launch {
            binding.editTextURL.addTextChangedListener { text ->
                lifecycleScope.launch { updateUserSettings { it.copy(lastGeneratedQRURL = text.toString()) } }
                qrCode = QREncoder(
                    this@GenerateQRCodeActivity,
                    text.toString()
                ).generate()
                binding.qrCode.setImageBitmap(qrCode)
            }
            binding.editTextURL.setText(getUserSettings().lastGeneratedQRURL)
            binding.editTextURL.requestFocus()
            binding.editTextURL.text?.let { binding.editTextURL.setSelection(0, it.length) }

        }
    }
}