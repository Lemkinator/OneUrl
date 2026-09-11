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

package de.lemke.oneurl.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Paint.Style.FILL
import android.graphics.Paint.Style.STROKE
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.annotation.ColorInt
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.toColorInt
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.ByteMatrix
import com.google.zxing.qrcode.encoder.Encoder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Hashtable
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import de.lemke.commonutils.R as commonutilsR
import dev.oneuiproject.oneui.design.R as oneuiR

/**
 * Renders the same QR code [GenerateQRCodeUseCase] produces (icon overlay, default colors, rounded
 * frame) natively at [sizePx], instead of generating at QrEncoder's fixed 200dp and downscaling.
 *
 * QrEncoder draws every module as an individually anti-aliased `canvas.drawCircle()` and re-decodes
 * the anchor/icon drawables on every call - at a small thumbnail size that work (and the two full
 * 200dp-scale bitmap allocations it happens in) is far larger than the output needs. QrEncoder is a
 * read-only reference library with `generate()` neither `open` nor built from overridable parts, and
 * its icon size is a fixed dp regardless of requested QR size (it would swallow a small code), so
 * this reimplements its drawing math - module circles, three finder-pattern anchors, a centered icon,
 * rounded frame - scaled uniformly down from QrEncoder's 200dp-code + 12dp-border (=224dp total)
 * reference geometry to [sizePx]. That is analytically what generate-then-scale([sizePx]) already
 * produces today, without ever allocating or drawing at the full reference size. The rasterized
 * anchor/icon bitmaps are cached per size since they don't depend on the URL.
 */
@Singleton
class GenerateQRCodeThumbnailUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val generateQRCode: GenerateQRCodeUseCase,
) {
    private val anchorCache = ConcurrentHashMap<Int, Bitmap>()
    private val iconCache = ConcurrentHashMap<Int, Bitmap>()

    // Broad catch is intentional: this reimplements a third-party drawing routine with no
    // documented exception contract; falls back to the slow-but-known-correct path rather than
    // crashing or producing a malformed thumbnail.
    @Suppress("TooGenericExceptionCaught")
    operator fun invoke(
        url: String,
        sizePx: Int,
    ): Bitmap =
        try {
            render(url, sizePx)
        } catch (e: Exception) {
            Log.e("GenerateQRCodeThumbnailUseCase", "error: ${e.message}", e)
            generateQRCode(url).scale(sizePx, sizePx)
        }

    private fun render(
        url: String,
        sizePx: Int,
    ): Bitmap {
        val hashtable = Hashtable<EncodeHintType, String>()
        hashtable[EncodeHintType.CHARACTER_SET] = "utf-8"
        val matrix = Encoder.encode(url, ErrorCorrectionLevel.H, hashtable).matrix

        val border = ratio(sizePx, BORDER_DP)
        val radius = ratio(sizePx, RADIUS_DP)
        val strokeWidth = ratio(sizePx, BORDER_STROKE_DP)
        val inner = sizePx - 2 * border
        val moduleWidth = inner / matrix.width
        val circleRadius = MODULE_CIRCLE_RATIO * moduleWidth
        val anchorWidth = leadingAnchorModuleCount(matrix) * moduleWidth
        val iconSize = ratio(sizePx, ICON_DP)
        val iconPadding = ratio(sizePx, ICON_PADDING_DP)

        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val clearPaint = fillPaint(BACKGROUND_COLOR)

        canvas.drawRoundRect(RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()), radius, radius, clearPaint)
        canvas.translate(border, border)
        drawModules(canvas, matrix, moduleWidth, circleRadius)
        drawAnchors(canvas, inner, anchorWidth, clearPaint)
        drawIcon(canvas, inner, iconSize, iconPadding, clearPaint)
        canvas.translate(-border, -border)
        drawBorderStroke(canvas, sizePx.toFloat(), radius, strokeWidth)
        return bitmap
    }

    private fun drawModules(
        canvas: Canvas,
        matrix: ByteMatrix,
        moduleWidth: Float,
        circleRadius: Float,
    ) {
        val paint = fillPaint(FOREGROUND_COLOR)
        val offset = moduleWidth / 2f
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y].toInt() == 1) canvas.drawCircle(x * moduleWidth + offset, y * moduleWidth + offset, circleRadius, paint)
            }
        }
    }

    private fun drawAnchors(
        canvas: Canvas,
        innerSize: Float,
        anchorWidth: Float,
        clearPaint: Paint,
    ) {
        canvas.drawRect(RectF(0f, 0f, anchorWidth, anchorWidth), clearPaint)
        canvas.drawRect(RectF(innerSize - anchorWidth, 0f, innerSize, anchorWidth), clearPaint)
        canvas.drawRect(RectF(0f, innerSize - anchorWidth, anchorWidth, innerSize), clearPaint)

        val anchor = anchorBitmap(anchorWidth.roundToInt().coerceAtLeast(1))
        canvas.drawBitmap(anchor, 0f, 0f, null)
        canvas.drawBitmap(anchor, innerSize - anchor.width, 0f, null)
        canvas.drawBitmap(anchor, 0f, innerSize - anchor.height, null)
    }

    private fun drawIcon(
        canvas: Canvas,
        innerSize: Float,
        iconSize: Float,
        iconPadding: Float,
        clearPaint: Paint,
    ) {
        val iconSizeInt = iconSize.roundToInt().coerceAtLeast(1)
        val left = (innerSize - iconSizeInt) / 2f
        val top = (innerSize - iconSizeInt) / 2f
        val iconRadius = iconSizeInt / 2f
        val rect = RectF(left - iconPadding, top - iconPadding, left + iconSizeInt + iconPadding, top + iconSizeInt + iconPadding)
        canvas.drawRoundRect(rect, iconRadius, iconRadius, clearPaint)
        canvas.drawBitmap(iconBitmap(iconSizeInt), left, top, null)
    }

    private fun drawBorderStroke(
        canvas: Canvas,
        size: Float,
        radius: Float,
        strokeWidth: Float,
    ) {
        val paint =
            Paint().apply {
                isAntiAlias = true
                style = STROKE
                color = BORDER_COLOR
                this.strokeWidth = strokeWidth
            }
        val inset = strokeWidth / 2f
        canvas.drawRoundRect(RectF(inset, inset, size - inset, size - inset), radius, radius, paint)
    }

    private fun anchorBitmap(sizePx: Int): Bitmap =
        anchorCache.getOrPut(sizePx) { rasterizeScaled(oneuiR.drawable.oui_des_qr_code_anchor, sizePx) }

    private fun iconBitmap(sizePx: Int): Bitmap = iconCache.getOrPut(sizePx) { rasterize(commonutilsR.drawable.ic_launcher_themed, sizePx) }

    private fun rasterize(
        drawableRes: Int,
        sizePx: Int,
    ): Bitmap = rasterize(checkNotNull(AppCompatResources.getDrawable(context, drawableRes)), sizePx)

    private fun rasterize(
        drawable: Drawable,
        sizePx: Int,
    ): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    // oui_des_qr_code_anchor.xml's stroke width and inner-circle inset are fixed dp values, not
    // proportional to its bounds - drawing it directly at a small target size shrinks the gap
    // between the ring and the inner dot faster than the ring itself, collapsing them into a solid
    // blob. QrEncoder avoids this by rendering the drawable at its own intrinsic size (where those
    // fixed values are correct) and bitmap-scaling the whole raster down; reproduced here.
    private fun rasterizeScaled(
        drawableRes: Int,
        sizePx: Int,
    ): Bitmap {
        val drawable = checkNotNull(AppCompatResources.getDrawable(context, drawableRes))
        val intrinsicSize = drawable.intrinsicWidth.takeIf { it > 0 } ?: sizePx
        val raster = rasterize(drawable, intrinsicSize)
        return if (intrinsicSize == sizePx) raster else raster.scale(sizePx, sizePx)
    }

    private fun leadingAnchorModuleCount(matrix: ByteMatrix): Int {
        var count = 0
        while (count < matrix.width && matrix[count, 0].toInt() == 1) count++
        return count
    }

    private fun fillPaint(
        @ColorInt color: Int,
    ) = Paint().apply {
        isAntiAlias = true
        style = FILL
        this.color = color
    }

    // Ratio of QrEncoder's default 200dp-code + 2*12dp-border (=224dp total) reference geometry;
    // rendering at sizePx via these ratios reproduces what generate-then-scale(sizePx) already
    // produces today, without ever drawing at the full reference size.
    private fun ratio(
        sizePx: Int,
        referenceDp: Int,
    ): Float = sizePx * referenceDp / TOTAL_REFERENCE_DP.toFloat()

    companion object {
        private const val TOTAL_REFERENCE_DP = 224
        private const val BORDER_DP = 12
        private const val RADIUS_DP = 32
        private const val ICON_DP = 48
        private const val ICON_PADDING_DP = 5
        private const val BORDER_STROKE_DP = 2
        private const val MODULE_CIRCLE_RATIO = 0.382f
        private val FOREGROUND_COLOR = Color.BLACK
        private val BACKGROUND_COLOR = "#fcfcfc".toColorInt()
        private val BORDER_COLOR = "#d0d0d0".toColorInt()
    }
}
