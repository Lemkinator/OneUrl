package de.lemke.oneurl.ui.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import de.lemke.oneurl.R

class CardView(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs) {
    private var isIconView: Boolean = false
    private var isDividerViewVisible: Boolean = false
    private val parentView: FrameLayout
    private val containerView: LinearLayout
    private val titleTextView: TextView
    private val summaryTextView: TextView
    private lateinit var iconImageView: ImageView
    private var dividerView: View
    private var iconColor = 0
    private var iconDrawable: Drawable? = null
    private var titleText1: String? = null
    private var summaryText1: String? = null

    init {
        setStyleable(attrs)
        removeAllViews()
        if (isIconView) {
            inflate(context, R.layout.widget_cardview_icon, this)
            iconImageView = findViewById(R.id.cardview_icon)
            iconImageView.setImageDrawable(iconDrawable)
            if (iconColor != -1) {
                iconImageView.drawable.setTint(iconColor)
            }
        } else {
            inflate(context, R.layout.widget_cardview, this)
        }
        parentView = findViewById(R.id.cardview_main_container)
        containerView = findViewById(R.id.cardview_container)
        titleTextView = findViewById(R.id.cardview_title)
        titleTextView.text = titleText1
        summaryTextView = findViewById(R.id.cardview_summary)
        if (summaryText1 != null && summaryText1!!.isNotEmpty()) {
            summaryTextView.text = summaryText1
            summaryTextView.visibility = VISIBLE
        }
        dividerView = findViewById(R.id.cardview_divider)
        val lp = dividerView.layoutParams as MarginLayoutParams
        lp.marginStart = if (isIconView) ((resources.getDimensionPixelSize(R.dimen.cardview_icon_divider_margin_end)
                + resources.getDimensionPixelSize(R.dimen.cardview_icon_size)
                + resources.getDimensionPixelSize(R.dimen.cardview_icon_margin_end))
                - resources.getDimensionPixelSize(R.dimen.cardview_icon_margin_vertical)
                ) else resources.getDimensionPixelSize(R.dimen.cardview_icon_divider_margin_end)
        lp.marginEnd = resources.getDimensionPixelSize(R.dimen.cardview_icon_divider_margin_end)
        dividerView.visibility = if (isDividerViewVisible) VISIBLE else GONE
    }

    private fun setStyleable(attrs: AttributeSet) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.CardView)
        iconDrawable = a.getDrawable(R.styleable.CardView_IconDrawable)
        iconColor = a.getColor(R.styleable.CardView_IconColor, -1)
        titleText1 = a.getString(R.styleable.CardView_TitleText)
        summaryText1 = a.getString(R.styleable.CardView_SummaryText)
        isIconView = iconDrawable != null
        isDividerViewVisible = a.getBoolean(R.styleable.CardView_isDividerViewVisible, false)
        a.recycle()
    }

    fun setIconDrawable(drawable: Drawable?) {
        iconDrawable = drawable
        val isNewDrawableNotNull = iconDrawable != null
        if (isNewDrawableNotNull == isIconView) {
            if (isNewDrawableNotNull) {
                if (iconColor != -1) {
                    iconDrawable!!.setTint(iconColor)
                }
                iconImageView.setImageDrawable(iconDrawable)
            }
        } else {
            isIconView = isNewDrawableNotNull
            removeAllViews()
            if (isIconView) {
                inflate(context, R.layout.widget_cardview_icon, this)

                iconImageView = findViewById(R.id.cardview_icon)
                iconImageView.setImageDrawable(iconDrawable)
                if (iconColor != -1) {
                    iconImageView.drawable.setTint(iconColor)
                }
            } else {
                inflate(context, R.layout.widget_cardview, this)
            }
            titleTextView.text = titleText1
            if (summaryText1 != null && summaryText1!!.isNotEmpty()) {
                summaryTextView.text = summaryText1
                summaryTextView.visibility = VISIBLE
            }
            dividerView = findViewById(R.id.cardview_divider)
            val lp = dividerView.layoutParams as MarginLayoutParams
            lp.marginStart = if (isIconView) ((resources.getDimensionPixelSize(R.dimen.cardview_icon_divider_margin_end)
                    + resources.getDimensionPixelSize(R.dimen.cardview_icon_size)
                    + resources.getDimensionPixelSize(R.dimen.cardview_icon_margin_end))
                    - resources.getDimensionPixelSize(R.dimen.cardview_icon_margin_vertical)
                    ) else resources.getDimensionPixelSize(R.dimen.cardview_icon_divider_margin_end)
            lp.marginEnd = resources.getDimensionPixelSize(R.dimen.cardview_icon_divider_margin_end)
            dividerView.visibility = if (isDividerViewVisible) VISIBLE else GONE
        }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        isFocusable = enabled
        isClickable = enabled
        parentView.isEnabled = enabled
        containerView.alpha = if (enabled) 1.0f else 0.4f
    }

    var titleText: String?
        get() = titleText1
        set(title) {
            titleText1 = title
            titleTextView.text = titleText1
        }

    var summaryText: String?
        get() = summaryText1
        set(newText) {
            var text = newText
            if (text == null) text = ""
            summaryText1 = text
            summaryTextView.text = summaryText1
            if (summaryText1!!.isEmpty()) summaryTextView.visibility = GONE
            else summaryTextView.visibility = VISIBLE
        }

    fun setDividerVisible(visible: Boolean) {
        dividerView.visibility = if (visible) VISIBLE else GONE
    }
}