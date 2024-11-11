package de.lemke.oneurl.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.util.SeslRoundedCorner
import androidx.appcompat.util.SeslSubheaderRoundedCorner
import androidx.recyclerview.widget.RecyclerView

@SuppressLint("PrivateResource")
class ItemDecoration(context: Context) : RecyclerView.ItemDecoration() {
    private val divider: Drawable?
    private val roundedCorner: SeslSubheaderRoundedCorner

    init {
        val outValue = TypedValue()
        context.theme.resolveAttribute(androidx.appcompat.R.attr.isLightTheme, outValue, true)
        divider = AppCompatResources.getDrawable(
            context,
            if (outValue.data == 0) androidx.appcompat.R.drawable.sesl_list_divider_dark
            else androidx.appcompat.R.drawable.sesl_list_divider_light
        )!!
        roundedCorner = SeslSubheaderRoundedCorner(context)
        roundedCorner.roundedCorners = SeslRoundedCorner.ROUNDED_CORNER_ALL
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDraw(c, parent, state)
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val holder = parent.getChildViewHolder(child) as URLAdapter.ViewHolder
            if (!holder.isSeparator) {
                val top = (child.bottom + (child.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin)
                val bottom = divider!!.intrinsicHeight + top
                divider.setBounds(parent.left, top, parent.right, bottom)
                divider.draw(c)
            }
        }
    }

    override fun seslOnDispatchDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val holder = parent.getChildViewHolder(child) as URLAdapter.ViewHolder
            if (holder.isSeparator) roundedCorner.drawRoundedCorner(child, c)
        }
    }
}