package de.lemke.oneurl.ui.view

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import de.lemke.oneurl.R

class TipsItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : LinearLayout(context, attrs, defStyleAttr, defStyleRes) {
    private var iconContainer: FrameLayout
    private var itemIcon: AppCompatImageView
    private var titleTextView: AppCompatTextView
    private var summaryTextView: AppCompatTextView

    init {
        removeAllViews()
        val outValue = TypedValue()
        context.theme.resolveAttribute(androidx.appcompat.R.attr.listChoiceBackgroundIndicator, outValue, true)
        if (outValue.resourceId > 0) setBackgroundResource(outValue.resourceId)
        else Log.w(TAG, "Couldn't retrieve listChoiceBackgroundIndicator!")
        orientation = HORIZONTAL
        inflate(context, R.layout.view_oobe_tips_item_layout, this)
        iconContainer = findViewById(R.id.tips_item_icon_container)
        itemIcon = findViewById(R.id.tips_item_icon)
        titleTextView = findViewById(R.id.tips_item_title_text)
        summaryTextView = findViewById(R.id.tips_item_summary_text)
        titleTextView.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val horizontalPadding =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, VIEW_HORIZONTAL_PADDING, resources.displayMetrics).toInt()
        setPadding(horizontalPadding, 0, horizontalPadding, 0)
    }

    fun setIcon(@DrawableRes resId: Int) = setIcon(AppCompatResources.getDrawable(context, resId))

    private fun setIcon(icon: Drawable?) {
        val hasIcon = icon != null
        iconContainer.visibility = if (hasIcon) VISIBLE else GONE
        itemIcon.setImageDrawable(icon)
        val paddingSize = if (hasIcon) VIEW_HORIZONTAL_PADDING_ICON else VIEW_HORIZONTAL_PADDING
        val horizontalPadding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, paddingSize, resources.displayMetrics).toInt()
        setPadding(horizontalPadding, 0, horizontalPadding, 0)
    }

    fun setTitleText(titleText: CharSequence?) {
        titleTextView.text = titleText
    }

    fun setTitleColor(@ColorInt color: Int) = titleTextView.setTextColor(color)

    fun setSummaryText(summaryText: CharSequence?) {
        summaryTextView.visibility = if (summaryText.isNullOrEmpty()) GONE else VISIBLE
        summaryTextView.text = summaryText
    }

    companion object {
        private val TAG = TipsItemView::class.java.simpleName
        private const val VIEW_HORIZONTAL_PADDING = 32.0f
        private const val VIEW_HORIZONTAL_PADDING_ICON = 24.0f
    }
}
