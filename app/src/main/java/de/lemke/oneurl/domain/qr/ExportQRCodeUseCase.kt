package de.lemke.oneurl.domain.qr


import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ActivityContext
import de.lemke.oneurl.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class ExportQRCodeUseCase @Inject constructor(
    @ActivityContext private val context: Context,
) {
    operator fun invoke(uri: Uri?, qrCode: Bitmap, name: String) {
        if (uri == null) {
            Toast.makeText(
                context,
                context.getString(R.string.error_no_folder_selected),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val filename = "${name}_${SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.GERMANY).format(Date())}"
            .replace("https://", "")
            .replace("[^a-zA-Z0-9]+".toRegex(), "_")
            .replace("_+".toRegex(), "_")
            .replace("^_".toRegex(), "") +
                ".png"
        val pngFile = DocumentFile.fromTreeUri(context, uri)!!.createFile("image/png", filename)
        val os = pngFile?.uri?.let { context.contentResolver.openOutputStream(it) }
        if (pngFile == null || os == null) {
            Toast.makeText(context, R.string.error_creating_file, Toast.LENGTH_SHORT).show()
            return
        }
        qrCode.compress(Bitmap.CompressFormat.PNG, 100, os)
        Toast.makeText(context, R.string.qr_saved, Toast.LENGTH_SHORT).show()
    }
}