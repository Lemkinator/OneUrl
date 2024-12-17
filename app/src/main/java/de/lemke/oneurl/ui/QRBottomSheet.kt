package de.lemke.oneurl.ui

import android.app.Activity.RESULT_OK
import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory.decodeByteArray
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.skydoves.bundler.bundleValue
import com.skydoves.bundler.intentOf
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.SaveLocation
import de.lemke.commonutils.exportBitmap
import de.lemke.commonutils.isSamsungQuickShareAvailable
import de.lemke.commonutils.quickShareBitmap
import de.lemke.commonutils.saveBitmapToUri
import de.lemke.commonutils.shareBitmap
import de.lemke.oneurl.databinding.ViewQrBottomsheetBinding
import java.io.ByteArrayOutputStream
import kotlin.apply

@AndroidEntryPoint
class QRBottomSheet : BottomSheetDialogFragment() {
    private lateinit var binding: ViewQrBottomsheetBinding
    private var qr: Bitmap? = null
    private val exportQRCodeResultLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        StartActivityForResult(),
        ActivityResultCallback<ActivityResult> { result ->
            if (result.resultCode == RESULT_OK) requireContext().saveBitmapToUri(result.data?.data, qr)
        }
    )

    companion object {
        const val KEY_TITLE = "key_title"
        const val KEY_QR = "key_qr"
        const val KEY_SAVE_LOCATION = "key_save_location"

        fun newInstance(title: String, qrCode: Bitmap, saveLocation: SaveLocation): QRBottomSheet =
            QRBottomSheet().apply {
                arguments = intentOf {
                    +(KEY_TITLE to title)
                    +(KEY_QR to qrCode.toByteArray())
                    +(KEY_SAVE_LOCATION to saveLocation.toString())
                }.extras
            }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return (super.onCreateDialog(savedInstanceState) as BottomSheetDialog).apply {
            behavior.skipCollapsed = true
            setOnShowListener { behavior.state = BottomSheetBehavior.STATE_EXPANDED }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ViewQrBottomsheetBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (requireContext().isSamsungQuickShareAvailable()) {
            binding.quickShareButton.visibility = View.VISIBLE
        }
        val shortURL: String = bundleValue(KEY_TITLE, "")
        val saveLocation: SaveLocation = SaveLocation.fromStringOrDefault(bundleValue(KEY_SAVE_LOCATION, ""))
        qr = bundleValue<ByteArray>(KEY_QR)?.toBitmap()
        binding.title.text = shortURL
        qr?.let { qrCode ->
            binding.qrCode.setImageBitmap(qrCode)
            binding.quickShareButton.setOnClickListener { requireContext().quickShareBitmap(qrCode, "QRCode.png") }
            binding.shareButton.setOnClickListener { requireContext().shareBitmap(qrCode, "QRCode.png") }
            binding.saveButton.setOnClickListener { exportBitmap(saveLocation, qrCode, shortURL, exportQRCodeResultLauncher) }
        }
    }
}

//java.lang.RuntimeException: Could not copy bitmap to parcel blob. ???????
private fun Bitmap.toByteArray(): ByteArray = ByteArrayOutputStream().use { stream ->
    compress(Bitmap.CompressFormat.PNG, 100, stream)
    stream.toByteArray()
}

private fun ByteArray.toBitmap(): Bitmap = decodeByteArray(this, 0, size)