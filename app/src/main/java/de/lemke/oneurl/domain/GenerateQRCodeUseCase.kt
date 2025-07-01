package de.lemke.oneurl.domain


import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color.BLACK
import android.graphics.Paint
import android.graphics.Paint.Style.FILL
import android.graphics.Paint.Style.STROKE
import android.graphics.RectF
import android.util.Log
import android.util.TypedValue.COMPLEX_UNIT_DIP
import android.util.TypedValue.applyDimension
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.oneuiproject.oneui.qr.QREncoder
import javax.inject.Inject
import de.lemke.commonutils.R as commonutilsR


class GenerateQRCodeUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    operator fun invoke(url: String): Bitmap {
        return try {
            QREncoder(context, url)
                .setIcon(commonutilsR.drawable.ic_launcher_themed)
                .generate()
                ?: generateNoSupportBitmap()
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("GenerateQRCodeUseCase", "error: ${e.message}")
            generateNoSupportBitmap()
        }
    }

    private fun generateNoSupportBitmap(): Bitmap {
        val size = getPixel(200)
        val result = createBitmap(size, size)
        result.eraseColor("#fcfcfc".toColorInt())
        drawIcon(result)
        val canvas = Canvas(result)
        val paint = Paint()
        paint.color = BLACK
        paint.alpha = 255
        paint.textSize = getPixel(16).toFloat()
        paint.isAntiAlias = true
        val text1 = "QR Codes are not"
        val x1 = (result.width - paint.measureText(text1)) / 2
        val y1 = (result.height - paint.ascent() - paint.descent()) * 11 / 16
        canvas.drawText(text1, x1, y1, paint)
        val text2 = "supported by this provider"
        val x2 = (result.width - paint.measureText(text2)) / 2
        val y2 = y1 + paint.descent() - paint.ascent()
        canvas.drawText(text2, x2, y2, paint)
        return addFrame(result)
    }

    private fun addFrame(qrcode: Bitmap): Bitmap {
        val border: Int = getPixel(12)
        val radius: Int = getPixel(32)
        val newWidth = qrcode.width + border * 2
        val newHeight = qrcode.height + border * 2
        val output = createBitmap(newWidth, newHeight)
        val canvas = Canvas(output)
        val paint: Paint = getPaint()
        val rectF = RectF(0f, 0f, newWidth.toFloat(), newHeight.toFloat())
        canvas.drawRoundRect(rectF, radius.toFloat(), radius.toFloat(), paint)
        canvas.drawBitmap(qrcode, border.toFloat(), border.toFloat(), null)
        paint.color = "#d0d0d0".toColorInt()
        paint.strokeWidth = 2f
        paint.style = STROKE
        rectF[1.0f, 1.0f, (newWidth - 1).toFloat()] = (newHeight - 1).toFloat()
        canvas.drawRoundRect(rectF, radius.toFloat(), radius.toFloat(), paint)
        return output
    }

    private fun drawIcon(bitmap: Bitmap) {
        val icon = AppCompatResources.getDrawable(context, commonutilsR.drawable.ic_launcher_themed) ?: return
        val size = getPixel(40)
        val iconTop = bitmap.height / 2 - size / 2
        val iconLeft = bitmap.width / 2 - size / 2
        val iconRadius = getPixel(20)
        val iconPadding = getPixel(5)
        val canvas = Canvas(bitmap)
        val rectF = RectF(
            (iconLeft - iconPadding).toFloat(),
            (iconTop - iconPadding).toFloat(),
            (size + iconLeft + iconPadding).toFloat(),
            (size + iconTop + iconPadding).toFloat()
        )
        canvas.drawRoundRect(rectF, iconRadius.toFloat(), iconRadius.toFloat(), getPaint())
        icon.setBounds(iconLeft, iconTop, iconLeft + size, iconTop + size)
        icon.draw(canvas)
    }

    private fun getPaint(): Paint {
        val paint = Paint()
        paint.isAntiAlias = true
        paint.style = FILL
        paint.color = "#fcfcfc".toColorInt()
        return paint
    }

    private fun getPixel(dp: Int): Int = if (context.resources == null) 0
    else applyDimension(COMPLEX_UNIT_DIP, dp.toFloat(), context.resources.displayMetrics).toInt()
}