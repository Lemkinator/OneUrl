package de.lemke.oneurl.ui

import android.annotation.SuppressLint
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
import de.lemke.oneurl.domain.model.ShortURLProvider.*

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

    private fun openLink(link: String?) = try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, getString(R.string.no_browser_app_installed), Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("SetTextI18n")
    private fun initProvider() {
        binding.dagdGroup.setOnClickListener { toggleGroup(binding.dagdGroup, binding.dagdGroupArrow, binding.dagdContent) }
        binding.dagdContentButtonMoreInformation.setOnClickListener { openLink(DAGD.infoURL) }

        binding.vgdIsgdGroup.setOnClickListener { toggleGroup(binding.vgdIsgdGroup, binding.vgdIsgdGroupArrow, binding.vgdIsgdContent) }
        binding.vgdContentButtonMoreInformation.text = getString(R.string.more_information) + " ($VGD)"
        binding.vgdContentButtonMoreInformation.setOnClickListener { openLink(VGD.infoURL) }
        binding.vgdContentButtonPrivacy.text = getString(R.string.privacy_policy) + " ($VGD)"
        binding.vgdContentButtonPrivacy.setOnClickListener { openLink(VGD.privacyURL) }
        binding.vgdContentButtonTerms.text = getString(R.string.tos) + " ($VGD)"
        binding.vgdContentButtonTerms.setOnClickListener { openLink(VGD.termsURL) }
        binding.isgdContentButtonMoreInformation.text = getString(R.string.more_information) + " ($ISGD)"
        binding.isgdContentButtonMoreInformation.setOnClickListener { openLink(ISGD.infoURL) }
        binding.isgdContentButtonPrivacy.text = getString(R.string.privacy_policy) + " ($ISGD)"
        binding.isgdContentButtonPrivacy.setOnClickListener { openLink(ISGD.privacyURL) }
        binding.isgdContentButtonTerms.text = getString(R.string.tos) + " ($ISGD)"
        binding.isgdContentButtonTerms.setOnClickListener { openLink(ISGD.termsURL) }

        binding.tinyurlGroup.setOnClickListener { toggleGroup(binding.tinyurlGroup, binding.tinyurlGroupArrow, binding.tinyurlContent) }
        binding.tinyurlContentButtonMoreInformation.setOnClickListener { openLink(TINYURL.infoURL) }
        binding.tinyurlContentButtonPrivacy.setOnClickListener { openLink(TINYURL.privacyURL) }
        binding.tinyurlContentButtonTerms.setOnClickListener { openLink(TINYURL.termsURL) }

        binding.kurzelinksGroup.setOnClickListener { toggleGroup(binding.kurzelinksGroup, binding.kurzelinksGroupArrow, binding.kurzelinksContent) }
        binding.kurzelinksContentButtonMoreInformation.setOnClickListener { openLink(KURZELINKS.infoURL) }
        binding.kurzelinksContentButtonPrivacy.setOnClickListener { openLink(KURZELINKS.privacyURL) }
        binding.kurzelinksContentButtonTerms.setOnClickListener { openLink(KURZELINKS.termsURL) }

        binding.ulvisGroup.setOnClickListener { toggleGroup(binding.ulvisGroup, binding.ulvisGroupArrow, binding.ulvisContent) }
        binding.ulvisContentButtonMoreInformation.setOnClickListener { openLink(ULVIS.infoURL) }
        binding.ulvisContentButtonPrivacy.setOnClickListener { openLink(ULVIS.privacyURL) }
        binding.ulvisContentButtonTerms.setOnClickListener { openLink(ULVIS.termsURL) }

        binding.oneptcoGroup.setOnClickListener { toggleGroup(binding.oneptcoGroup, binding.oneptcoGroupArrow, binding.oneptcoContent) }
        binding.oneptcoContentButtonMoreInformation.setOnClickListener { openLink(ONEPTCO.infoURL) }

        binding.shareaholicGroup.setOnClickListener {
            toggleGroup(binding.shareaholicGroup, binding.shareaholicGroupArrow, binding.shareaholicContent)
        }
        binding.shareaholicContentButtonMoreInformation.setOnClickListener { openLink(SHAREAHOLIC.infoURL) }
        binding.shareaholicContentButtonPrivacy.setOnClickListener { openLink(SHAREAHOLIC.privacyURL) }
        binding.shareaholicContentButtonTerms.setOnClickListener { openLink(SHAREAHOLIC.termsURL) }

        binding.owovcGroup.setOnClickListener { toggleGroup(binding.owovcGroup, binding.owovcGroupArrow, binding.owovcContent) }
        binding.owovcContentButtonMoreInformation.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OWOVC.infoURL)))
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