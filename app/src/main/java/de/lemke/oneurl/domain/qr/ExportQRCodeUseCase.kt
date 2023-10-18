package de.lemke.oneurl.domain.qr


import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ActivityContext
import de.lemke.oneurl.R
import dev.oneuiproject.oneui.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class ExportQRCodeUseCase @Inject constructor(
    @ActivityContext private val context: Context,
) {
    operator fun invoke(uri: Uri, qrCode: Bitmap, name: String) {
        val timestamp = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.GERMANY).format(Date())
        val pngFile = DocumentFile.fromTreeUri(context, uri)!!.createFile("image/png", "${name}_$timestamp")
        qrCode.compress(Bitmap.CompressFormat.PNG, 100, context.contentResolver.openOutputStream(pngFile!!.uri)!!)
        Toast.makeText(context, R.string.qr_saved, Toast.LENGTH_LONG).show()
    }
}