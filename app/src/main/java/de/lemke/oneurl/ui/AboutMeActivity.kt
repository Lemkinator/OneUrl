package de.lemke.oneurl.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.TooltipCompat
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.AppBarLayout.OnOffsetChangedListener
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.oneurl.R
import de.lemke.oneurl.databinding.ActivityAboutMeBinding
import de.lemke.oneurl.databinding.ActivityAboutMeContentBinding
import de.lemke.oneurl.domain.OpenAppUseCase
import de.lemke.oneurl.domain.OpenLinkUseCase
import dev.oneuiproject.oneui.utils.ViewUtils
import dev.oneuiproject.oneui.utils.internal.ToolbarLayoutUtils
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class AboutMeActivity : AppCompatActivity(), View.OnClickListener {
    private lateinit var binding: ActivityAboutMeBinding
    private lateinit var bottomContent: ActivityAboutMeContentBinding
    private var lastClickTime: Long = 0
    private val appBarListener: AboutMeAppBarListener = AboutMeAppBarListener()

    @Inject
    lateinit var openApp: OpenAppUseCase

    @Inject
    lateinit var openLink: OpenLinkUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutMeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bottomContent = binding.aboutBottomContent
        setSupportActionBar(binding.aboutToolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowTitleEnabled(false)
        binding.aboutToolbar.setNavigationOnClickListener { finish() }
        resetAppBar(resources.configuration)
        initContent()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        resetAppBar(newConfig)
    }

    @SuppressLint("RestrictedApi")
    private fun resetAppBar(config: Configuration) {
        ToolbarLayoutUtils.hideStatusBarForLandscape(this, config.orientation)
        ToolbarLayoutUtils.updateListBothSideMargin(this, binding.aboutBottomContainer)
        if (config.orientation != Configuration.ORIENTATION_LANDSCAPE && !isInMultiWindowMode) {
            binding.aboutAppBar.seslSetCustomHeightProportion(true, 0.5f)
            binding.aboutAppBar.addOnOffsetChangedListener(appBarListener)
            binding.aboutAppBar.setExpanded(true, false)
            binding.aboutSwipeUpContainer.visibility = View.VISIBLE
            val lp: ViewGroup.LayoutParams = binding.aboutSwipeUpContainer.layoutParams
            lp.height = resources.displayMetrics.heightPixels / 2
        } else {
            binding.aboutAppBar.setExpanded(false, false)
            binding.aboutAppBar.seslSetCustomHeightProportion(true, 0F)
            binding.aboutAppBar.removeOnOffsetChangedListener(appBarListener)
            binding.aboutBottomContainer.alpha = 1f
            setBottomContentEnabled(true)
            binding.aboutSwipeUpContainer.visibility = View.GONE
        }
    }

    private fun initContent() {
        ViewUtils.semSetRoundedCorners(
            binding.aboutBottomContent.root,
            ViewUtils.SEM_ROUNDED_CORNER_TOP_LEFT or ViewUtils.SEM_ROUNDED_CORNER_TOP_RIGHT
        )
        ViewUtils.semSetRoundedCornerColor(
            binding.aboutBottomContent.root,
            ViewUtils.SEM_ROUNDED_CORNER_TOP_LEFT or ViewUtils.SEM_ROUNDED_CORNER_TOP_RIGHT,
            getColor(dev.oneuiproject.oneui.design.R.color.oui_round_and_bgcolor)
        )
        val appIcon = AppCompatResources.getDrawable(this, R.drawable.me4_round)
        binding.aboutHeaderIcon.setImageDrawable(appIcon)
        binding.aboutBottomIcon.setImageDrawable(appIcon)
        binding.aboutHeaderGithub.setOnClickListener(this)
        binding.aboutHeaderWebsite.setOnClickListener(this)
        binding.aboutHeaderPlayStore.setOnClickListener(this)
        binding.aboutHeaderInsta.setOnClickListener(this)
        binding.aboutHeaderTiktok.setOnClickListener(this)
        TooltipCompat.setTooltipText(binding.aboutHeaderGithub, getString(R.string.github))
        bottomContent.aboutBottomRateApp.setOnClickListener(this)
        bottomContent.aboutBottomShareApp.setOnClickListener(this)
        bottomContent.aboutBottomWriteEmail.setOnClickListener(this)
        bottomContent.aboutBottomRelativeTiktok.setOnClickListener(this)
        bottomContent.aboutBottomRelativeWebsite.setOnClickListener(this)
        bottomContent.aboutBottomRelativePlayStore.setOnClickListener(this)
    }

    private fun setBottomContentEnabled(enabled: Boolean) {
        binding.aboutHeaderGithub.isEnabled = !enabled
        binding.aboutHeaderWebsite.isEnabled = !enabled
        binding.aboutHeaderPlayStore.isEnabled = !enabled
        binding.aboutHeaderInsta.isEnabled = !enabled
        binding.aboutHeaderTiktok.isEnabled = !enabled
        bottomContent.aboutBottomRateApp.isEnabled = enabled
        bottomContent.aboutBottomShareApp.isEnabled = enabled
        bottomContent.aboutBottomWriteEmail.isEnabled = enabled
        bottomContent.aboutBottomRelativeTiktok.isEnabled = enabled
        bottomContent.aboutBottomRelativeWebsite.isEnabled = enabled
        bottomContent.aboutBottomRelativePlayStore.isEnabled = enabled
    }

    override fun onClick(v: View) {
        val uptimeMillis = SystemClock.uptimeMillis()
        if (uptimeMillis - lastClickTime > 600L) {
            when (v.id) {
                binding.aboutHeaderWebsite.id, bottomContent.aboutBottomRelativeWebsite.id -> openLink(getString(R.string.my_website))
                binding.aboutHeaderGithub.id -> openLink(getString(R.string.my_github))
                binding.aboutHeaderPlayStore.id, bottomContent.aboutBottomRelativePlayStore.id -> openLink(getString(R.string.playstore_developer_page_link))
                binding.aboutHeaderTiktok.id, bottomContent.aboutBottomRelativeTiktok.id -> openLink(getString(R.string.rick_roll_troll_link))
                binding.aboutHeaderInsta.id -> openLink(getString(R.string.my_insta))
                bottomContent.aboutBottomRateApp.id -> openApp(packageName, false)
                bottomContent.aboutBottomShareApp.id -> startActivity(Intent.createChooser(Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, getString(R.string.playstore_link) + packageName)
                }, null))

                bottomContent.aboutBottomWriteEmail.id -> {
                    val intent = Intent(Intent.ACTION_SENDTO)
                    intent.data = Uri.parse("mailto:") // only email apps should handle this
                    intent.putExtra(Intent.EXTRA_EMAIL, arrayOf(getString(R.string.email)))
                    intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
                    intent.putExtra(Intent.EXTRA_TEXT, "")
                    try {
                        startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        e.printStackTrace()
                        Toast.makeText(this@AboutMeActivity, getString(R.string.no_email_app_installed), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        lastClickTime = uptimeMillis
    }

    private inner class AboutMeAppBarListener : OnOffsetChangedListener {
        override fun onOffsetChanged(appBarLayout: AppBarLayout, verticalOffset: Int) {
            // Handle the SwipeUp anim view
            val totalScrollRange = appBarLayout.totalScrollRange
            val abs = abs(verticalOffset)
            if (abs >= totalScrollRange / 2) {
                binding.aboutSwipeUpContainer.alpha = 0f
                setBottomContentEnabled(true)
            } else if (abs == 0) {
                binding.aboutSwipeUpContainer.alpha = 1f
                setBottomContentEnabled(false)
            } else {
                val offsetAlpha = appBarLayout.y / totalScrollRange
                var arrowAlpha = 1 - offsetAlpha * -3
                if (arrowAlpha < 0) {
                    arrowAlpha = 0f
                } else if (arrowAlpha > 1) {
                    arrowAlpha = 1f
                }
                binding.aboutSwipeUpContainer.alpha = arrowAlpha
            }

            // Handle the bottom part of the UI
            val alphaRange: Float = binding.aboutCtl.height * 0.143f
            val layoutPosition = abs(appBarLayout.top).toFloat()
            var bottomAlpha: Float = (150.0f / alphaRange * (layoutPosition - binding.aboutCtl.height * 0.35f))
            if (bottomAlpha < 0) {
                bottomAlpha = 0f
            } else if (bottomAlpha >= 255) {
                bottomAlpha = 255f
            }
            binding.aboutBottomContainer.alpha = bottomAlpha / 255
        }
    }
}