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

package de.lemke.oneurl.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color.BLACK
import android.graphics.Paint
import android.graphics.Paint.Style.FILL
import android.graphics.Paint.Style.STROKE
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.TypedValue.COMPLEX_UNIT_DIP
import android.util.TypedValue.applyDimension
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.model.QRCodeStyle
import dev.oneuiproject.oneui.qr.utils.QrEncoder
import javax.inject.Inject
import de.lemke.commonutils.R as commonutilsR

interface QRCodeRenderer {
    /**
     * Encodes [url] as a QR code drawn in [style], or in the encoder's default look with the launcher icon when [style] is null.
     * Returns null when the encoder produces no bitmap.
     */
    fun render(
        url: String,
        style: QRCodeStyle?,
    ): Bitmap?

    /** Draws the card shown in place of a QR code that cannot be rendered. */
    fun placeholder(): Bitmap
}

class DefaultQRCodeRenderer @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : QRCodeRenderer {
    override fun render(
        url: String,
        style: QRCodeStyle?,
    ): Bitmap? =
        QrEncoder(context, url)
            .apply { if (style == null) setIcon(LAUNCHER_ICON) else applyStyle(style) }
            .generate()

    override fun placeholder(): Bitmap = drawQRCodePlaceholder(context, AppCompatResources.getDrawable(context, LAUNCHER_ICON))

    private fun QrEncoder.applyStyle(style: QRCodeStyle) {
        if (style.icon) setIcon(LAUNCHER_ICON)
        setBackgroundColor(style.backgroundColor)
        setForegroundColor(style.foregroundColor, style.tintAnchor, style.tintBorder)
        setSize(style.size)
        roundedFrame(style.roundedFrame)
    }

    private companion object {
        val LAUNCHER_ICON = commonutilsR.drawable.ic_launcher_themed
    }
}

/** Draws the "not supported" card: the localized notice below [icon] on a light rounded card, or the notice alone without [icon]. */
internal fun drawQRCodePlaceholder(
    context: Context,
    icon: Drawable?,
): Bitmap {
    val size = context.dpToPx(PLACEHOLDER_SIZE_DP)
    val card = createBitmap(size, size)
    card.eraseColor(CARD_COLOR.toColorInt())
    if (icon != null) card.drawCenteredIcon(context, icon)
    card.drawNotice(context, context.getString(R.string.qr_code_not_supported).lines())
    return card.withFrame(context)
}

private fun Bitmap.drawCenteredIcon(
    context: Context,
    icon: Drawable,
) {
    val size = context.dpToPx(ICON_SIZE_DP)
    val top = height / 2 - size / 2
    val left = width / 2 - size / 2
    val radius = context.dpToPx(ICON_RADIUS_DP).toFloat()
    val padding = context.dpToPx(ICON_PADDING_DP)
    val canvas = Canvas(this)
    val background =
        RectF(
            (left - padding).toFloat(),
            (top - padding).toFloat(),
            (left + size + padding).toFloat(),
            (top + size + padding).toFloat(),
        )
    canvas.drawRoundRect(background, radius, radius, cardPaint())
    icon.setBounds(left, top, left + size, top + size)
    icon.draw(canvas)
}

private fun Bitmap.drawNotice(
    context: Context,
    lines: List<String>,
) {
    val canvas = Canvas(this)
    val paint =
        Paint().apply {
            color = BLACK
            textSize = context.dpToPx(NOTICE_TEXT_SIZE_DP).toFloat()
            isAntiAlias = true
        }
    val lineHeight = paint.descent() - paint.ascent()
    val firstBaseline = (height - paint.ascent() - paint.descent()) * NOTICE_TOP_SIXTEENTHS / SIXTEENTHS
    lines.forEachIndexed { index, line ->
        canvas.drawText(line, (width - paint.measureText(line)) / 2, firstBaseline + index * lineHeight, paint)
    }
}

private fun Bitmap.withFrame(context: Context): Bitmap {
    val border = context.dpToPx(FRAME_BORDER_DP)
    val radius = context.dpToPx(FRAME_RADIUS_DP).toFloat()
    val framedWidth = width + border * 2
    val framedHeight = height + border * 2
    val framed = createBitmap(framedWidth, framedHeight)
    val canvas = Canvas(framed)
    val paint = cardPaint()
    canvas.drawRoundRect(RectF(0f, 0f, framedWidth.toFloat(), framedHeight.toFloat()), radius, radius, paint)
    canvas.drawBitmap(this, border.toFloat(), border.toFloat(), null)
    paint.color = FRAME_STROKE_COLOR.toColorInt()
    paint.strokeWidth = FRAME_STROKE_WIDTH_PX
    paint.style = STROKE
    canvas.drawRoundRect(RectF(1f, 1f, (framedWidth - 1).toFloat(), (framedHeight - 1).toFloat()), radius, radius, paint)
    return framed
}

private fun cardPaint(): Paint =
    Paint().apply {
        isAntiAlias = true
        style = FILL
        color = CARD_COLOR.toColorInt()
    }

private fun Context.dpToPx(dp: Int): Int = applyDimension(COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()

private const val CARD_COLOR = "#fcfcfc"
private const val FRAME_STROKE_COLOR = "#d0d0d0"
private const val FRAME_STROKE_WIDTH_PX = 2f
private const val PLACEHOLDER_SIZE_DP = 200
private const val ICON_SIZE_DP = 40
private const val ICON_RADIUS_DP = 20
private const val ICON_PADDING_DP = 5
private const val FRAME_BORDER_DP = 12
private const val FRAME_RADIUS_DP = 32
private const val NOTICE_TEXT_SIZE_DP = 16
private const val NOTICE_TOP_SIXTEENTHS = 11
private const val SIXTEENTHS = 16
