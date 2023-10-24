package de.lemke.oneurl.domain


import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import android.util.TypedValue
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lemke.oneurl.R
import dev.oneuiproject.oneui.qr.QREncoder
import javax.inject.Inject


class GenerateQRCodeUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    operator fun invoke(url: String): Bitmap {
        return try {
            QREncoder(context, url)
                .setIcon(R.drawable.ic_launcher_themed)
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
        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        result.eraseColor(Color.parseColor("#fcfcfc"))
        drawIcon(result)
        val canvas = Canvas(result)
        val paint = Paint()
        paint.color = Color.BLACK
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
        val output = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint: Paint = getPaint()
        val rectF = RectF(0f, 0f, newWidth.toFloat(), newHeight.toFloat())
        canvas.drawRoundRect(rectF, radius.toFloat(), radius.toFloat(), paint)
        canvas.drawBitmap(qrcode, border.toFloat(), border.toFloat(), null)
        paint.color = Color.parseColor("#d0d0d0")
        paint.strokeWidth = 2f
        paint.style = Paint.Style.STROKE
        rectF[1.0f, 1.0f, (newWidth - 1).toFloat()] = (newHeight - 1).toFloat()
        canvas.drawRoundRect(rectF, radius.toFloat(), radius.toFloat(), paint)
        return output
    }

    private fun drawIcon(bitmap: Bitmap) {
        val icon = context.getDrawable(R.drawable.ic_launcher_themed) ?: return
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
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#fcfcfc")
        return paint
    }

    private fun getPixel(dp: Int): Int = if (context.resources == null) 0
    else TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), context.resources.displayMetrics).toInt()
}