package de.lemke.oneurl.domain.qr


import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ActivityContext
import de.lemke.oneurl.R
import java.io.File
import javax.inject.Inject

class CopyQRCodeUseCase @Inject constructor(
    @ActivityContext private val context: Context,
) {
    operator fun invoke(qrCode: Bitmap) {
        val cacheFile = File(context.cacheDir, "qr.png")
        qrCode.compress(Bitmap.CompressFormat.PNG, 100, cacheFile.outputStream())
        val uri = FileProvider.getUriForFile(context, "de.lemke.oneurl.fileprovider", cacheFile)
        val clip = ClipData.newUri(context.contentResolver, "qr-code", uri)
        (context.getSystemService(AppCompatActivity.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
        Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }
}