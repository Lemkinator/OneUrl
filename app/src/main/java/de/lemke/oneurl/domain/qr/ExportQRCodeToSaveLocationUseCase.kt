package de.lemke.oneurl.domain.qr


import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.util.Log
import android.widget.Toast
import dagger.hilt.android.qualifiers.ActivityContext
import de.lemke.oneurl.R
import de.lemke.oneurl.data.SaveLocation
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class ExportQRCodeToSaveLocationUseCase @Inject constructor(
    @ActivityContext private val context: Context,
) {
    operator fun invoke(saveLocation: SaveLocation, qrCode: Bitmap, name: String) {
        try {
            val dir: String = when (saveLocation) {
                SaveLocation.DOWNLOADS -> Environment.DIRECTORY_DOWNLOADS
                SaveLocation.PICTURES -> Environment.DIRECTORY_PICTURES
                SaveLocation.DCIM -> Environment.DIRECTORY_DCIM
                SaveLocation.CUSTOM -> Environment.DIRECTORY_PICTURES // should never happen
            }
            val fileName = "${name}_${SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.GERMANY).format(Date())}"
                .replace("https://", "")
                .replace("[^a-zA-Z0-9]+".toRegex(), "_")
                .replace("_+".toRegex(), "_")
                .replace("^_".toRegex(), "") +
                    ".png"
            val os: OutputStream = Files.newOutputStream(File(Environment.getExternalStoragePublicDirectory(dir), fileName).toPath())
            qrCode.compress(Bitmap.CompressFormat.PNG, 100, os)
            os.close()
            Toast.makeText(context, context.getString(R.string.qr_saved) + ": ${saveLocation.toLocalizedString(context)}", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            Toast.makeText(context, context.getString(R.string.error_creating_file), Toast.LENGTH_SHORT).show()
            Log.e("ExportQRCodeToSaveLocationUseCase", e.message.toString())
            e.printStackTrace()
        }
    }
}