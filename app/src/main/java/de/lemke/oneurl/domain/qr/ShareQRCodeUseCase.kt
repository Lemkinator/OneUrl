package de.lemke.oneurl.domain.qr


import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ActivityContext
import java.io.File
import javax.inject.Inject

class ShareQRCodeUseCase @Inject constructor(
    @ActivityContext private val context: Context,
) {
    operator fun invoke(qrCode: Bitmap) {
        val cacheFile = File(context.cacheDir, "qr.png")
        qrCode.compress(Bitmap.CompressFormat.PNG, 100, cacheFile.outputStream())
        val uri = FileProvider.getUriForFile(context, "de.lemke.oneurl.fileprovider", cacheFile)
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "image/png"
        }
        context.startActivity(Intent.createChooser(sendIntent, null))
    }
}