package de.lemke.oneurl.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.Transformation
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.oneurl.R
import de.lemke.oneurl.databinding.ActivityProviderBinding
import de.lemke.oneurl.domain.model.ShortURLProvider

@AndroidEntryPoint
class ProviderActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProviderBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProviderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbarLayout.setNavigationButtonOnClickListener { finish() }
        binding.toolbarLayout.tooltipText = getString(R.string.sesl_navigate_up)
        initProvider()
    }

    private fun toggleGroup(group: LinearLayout, groupArrow: ImageView, vararg content: LinearLayout) {
        if (group.isSelected) {
            group.isSelected = false
            groupArrow.animate().rotation(if (group.isSelected) 180f else 0f).setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator()).start()
            content.forEach { it.collapse() }
        } else {
            group.isSelected = true
            groupArrow.animate().rotation(if (group.isSelected) 180f else 0f).setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator()).start()
            content.forEach { it.expand() }
        }
    }

    private fun initProvider() {
        binding.dagdGroup.setOnClickListener { toggleGroup(binding.dagdGroup, binding.dagdGroupArrow, binding.dagdContent) }
        binding.dagdContentButtonMoreInformation.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ShortURLProvider.DAGD.baseURL)))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, getString(R.string.error), Toast.LENGTH_SHORT).show()
            }
        }
        binding.vgdIsgdGroup.setOnClickListener { toggleGroup(binding.vgdIsgdGroup, binding.vgdIsgdGroupArrow, binding.vgdIsgdContent) }
        binding.vgdIsgdContentButtonMoreInformation.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ShortURLProvider.VGD.baseURL)))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, getString(R.string.error), Toast.LENGTH_SHORT).show()
            }
        }
        binding.tinyurlGroup.setOnClickListener { toggleGroup(binding.tinyurlGroup, binding.tinyurlGroupArrow, binding.tinyurlContent) }
        binding.tinyurlContentButtonMoreInformation.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ShortURLProvider.TINYURL.baseURL)))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, getString(R.string.error), Toast.LENGTH_SHORT).show()
            }
        }
        binding.ulvisGroup.setOnClickListener { toggleGroup(binding.ulvisGroup, binding.ulvisGroupArrow, binding.ulvisContent) }
        binding.ulvisContentButtonMoreInformation.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ShortURLProvider.ULVIS.baseURL)))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, getString(R.string.error), Toast.LENGTH_SHORT).show()
            }
        }
        binding.oneptcoGroup.setOnClickListener { toggleGroup(binding.oneptcoGroup, binding.oneptcoGroupArrow, binding.oneptcoContent) }
        binding.oneptcoContentButtonMoreInformation.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ShortURLProvider.ONEPTCO.baseURL)))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, getString(R.string.error), Toast.LENGTH_SHORT).show()
            }
        }
        binding.owovcGroup.setOnClickListener { toggleGroup(binding.owovcGroup, binding.owovcGroupArrow, binding.owovcContent) }
        binding.owovcContentButtonMoreInformation.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ShortURLProvider.OWOVC.baseURL)))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, getString(R.string.error), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

fun View.expand() {
    measure(
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )
    val targetHeight = measuredHeight
    visibility = View.VISIBLE
    val animation: Animation = object : Animation() {
        override fun willChangeBounds() = true
        override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
            layoutParams.height =
                if (interpolatedTime == 1f) LinearLayout.LayoutParams.WRAP_CONTENT else (targetHeight * interpolatedTime).toInt()
            alpha = interpolatedTime
            requestLayout()
        }
    }
    // Expansion speed of 1dp/ms
    animation.duration = (targetHeight / context.resources.displayMetrics.density).toInt().toLong()
    animation.interpolator = AccelerateDecelerateInterpolator()
    startAnimation(animation)
}

fun View.collapse() {
    val initialHeight = measuredHeight
    val animation: Animation = object : Animation() {
        override fun willChangeBounds() = true
        override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
            if (interpolatedTime == 1f) visibility = View.GONE
            else {
                layoutParams.height = initialHeight - (initialHeight * interpolatedTime).toInt()
                requestLayout()
            }
            alpha = 1 - interpolatedTime
        }
    }
    // Collapse speed of 1dp/ms
    animation.duration = (initialHeight / context.resources.displayMetrics.density).toInt().toLong()
    animation.interpolator = AccelerateDecelerateInterpolator()
    startAnimation(animation)
}