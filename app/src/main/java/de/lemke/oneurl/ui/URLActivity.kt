package de.lemke.oneurl.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.style.UnderlineSpan
import android.view.Menu
import android.view.MenuItem
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.skydoves.bundler.bundleValue
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.SaveLocation
import de.lemke.commonutils.copyToClipboard
import de.lemke.commonutils.exportBitmap
import de.lemke.commonutils.openURL
import de.lemke.commonutils.prepareActivityTransformationTo
import de.lemke.commonutils.saveBitmapToUri
import de.lemke.commonutils.setCustomBackAnimation
import de.lemke.commonutils.setWindowTransparent
import de.lemke.commonutils.shareBitmap
import de.lemke.commonutils.shareText
import de.lemke.commonutils.showDimmingTipPopup
import de.lemke.commonutils.showInAppReviewOrFinish
import de.lemke.commonutils.toast
import de.lemke.oneurl.R
import de.lemke.oneurl.databinding.ActivityUrlBinding
import de.lemke.oneurl.domain.DeleteURLUseCase
import de.lemke.oneurl.domain.GetURLUseCase
import de.lemke.oneurl.domain.GetUserSettingsUseCase
import de.lemke.oneurl.domain.UpdateURLUseCase
import de.lemke.oneurl.domain.UpdateUserSettingsUseCase
import de.lemke.oneurl.domain.model.URL
import de.lemke.oneurl.domain.urlEncode
import de.lemke.oneurl.domain.withHttps
import de.lemke.oneurl.ui.ProviderInfoBottomSheet.Companion.showProviderInfoBottomSheet
import de.lemke.oneurl.ui.QRBottomSheet.Companion.createQRBottomSheet
import dev.oneuiproject.oneui.utils.SearchHighlighter
import kotlinx.coroutines.launch
import javax.inject.Inject
import dev.oneuiproject.oneui.design.R as designR

@AndroidEntryPoint
class URLActivity : AppCompatActivity() {
    companion object {
        const val KEY_SHORTURL = "key_shorturl"
        const val KEY_HIGHLIGHT_TEXT = "key_highlight_text"
    }

    private lateinit var binding: ActivityUrlBinding
    private lateinit var url: URL
    private lateinit var saveLocation: SaveLocation
    private lateinit var searchHighlighter: SearchHighlighter
    private val exportQRCodeResultLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        StartActivityForResult(),
        ActivityResultCallback<ActivityResult> { result ->
            if (result.resultCode == RESULT_OK && ::url.isInitialized) saveBitmapToUri(result.data?.data, url.qr)
        }
    )

    @Inject
    lateinit var getURL: GetURLUseCase

    @Inject
    lateinit var updateURL: UpdateURLUseCase

    @Inject
    lateinit var deleteURL: DeleteURLUseCase

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        prepareActivityTransformationTo()
        super.onCreate(savedInstanceState)
        binding = ActivityUrlBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setWindowTransparent(true)
        lifecycleScope.launch {
            getURL(bundleValue<String>(KEY_SHORTURL, "")).let {
                if (it == null) {
                    toast(R.string.error_url_not_found)
                    finishAfterTransition()
                    return@launch
                }
                url = it
            }
            val userSettings = getUserSettings()
            saveLocation = userSettings.saveLocation
            initViews()
            if (userSettings.showCopyHint) binding.urlShortButton.doOnLayout {
                it.postDelayed({
                    it.showDimmingTipPopup(R.string.copy_to_clipboard_hint, de.lemke.commonutils.R.string.dont_show_again) {
                        lifecycleScope.launch { updateUserSettings { it.copy(showCopyHint = false) } }
                    }
                }, 500)
            }
            setCustomBackAnimation(binding.root, showInAppReviewIfPossible = true)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean = menuInflater.inflate(R.menu.url_toolbar, menu).let { true }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.url_toolbar_norton_safe_web -> openURL("https://safeweb.norton.com/report/show?url=${url.longURL.urlEncode()}").let { true }
        R.id.url_toolbar_google_safe_browsing -> openURL("https://transparencyreport.google.com/safe-browsing/search?url=${url.longURL.urlEncode()}").let { true }
        R.id.url_toolbar_link_shield -> openURL("https://linkshieldapi.com/?url=${url.longURL.urlEncode()}").let { true }
        R.id.url_toolbar_malshare -> openURL("https://malshare.com/search.php?query=${url.longURL.urlEncode()}").let { true }
        R.id.url_toolbar_urlhaus -> openURL("https://urlhaus.abuse.ch/browse.php?search=${url.longURL.urlEncode()}").let { true }
        R.id.url_toolbar_kaspersky -> openURL("https://opentip.kaspersky.com/${url.longURL.urlEncode()}/?tab=lookup").let { true }
        else -> super.onOptionsItemSelected(item)
    }

    @SuppressLint("SetTextI18n")
    private fun refreshVisitCount() {
        binding.urlVisitsRefreshButton.isEnabled = false
        binding.urlVisitsRefreshButton.alpha = 0.5f
        binding.urlVisitsRefreshButton.rotation = 0f
        binding.urlVisitsRefreshButton.animate().rotationBy(-1080f).setDuration(2500).interpolator = AccelerateDecelerateInterpolator()
        url.shortURLProvider.getURLClickCount(this, url) { count ->
            if (count != null) {
                binding.urlVisitsDivider.isVisible = true
                binding.urlVisitsLayout.isVisible = true
                binding.urlVisitsTextview.text = count.toString()
            } else {
                binding.urlVisitsDivider.isVisible = false
                binding.urlVisitsLayout.isVisible = false
            }
            binding.urlVisitsRefreshButton.isEnabled = true
            binding.urlVisitsRefreshButton.alpha = 1f
        }
    }


    private fun initViews() {
        binding.root.setTitle(url.shortURL)
        searchHighlighter = SearchHighlighter(this)
        val highlightText: String = bundleValue(KEY_HIGHLIGHT_TEXT, "")
        binding.urlQrImageview.setImageBitmap(url.qr)
        binding.urlQrImageview.setOnClickListener {
            lifecycleScope.launch { createQRBottomSheet(url.shortURL, url.qr, saveLocation).show(supportFragmentManager, null) }
        }
        binding.urlQrImageview.setOnLongClickListener { url.qr.copyToClipboard(this, "QR Code", "QRCode.png").let { true } }
        binding.urlQrSaveButton.setOnClickListener { exportBitmap(saveLocation, url.qr, url.shortURL, exportQRCodeResultLauncher) }
        binding.urlQrShareButton.setOnClickListener { shareBitmap(url.qr, "QRCode.png") }
        binding.urlShortButton.text = searchHighlighter(url.shortURL, highlightText).apply {
            setSpan(UnderlineSpan(), 0, url.shortURL.length, 0)
        }
        binding.urlShortButton.setOnClickListener { openURL(url.shortURL.withHttps()) }
        binding.urlShortButton.setOnLongClickListener { copyToClipboard("Short URL", url.shortURL) }
        binding.urlShortShareButton.setOnClickListener { shareText(url.shortURL) }
        binding.urlLongButton.text = searchHighlighter(url.longURL, highlightText).apply {
            setSpan(UnderlineSpan(), 0, url.longURL.length, 0)
        }
        binding.urlLongButton.setOnClickListener { openURL(url.longURL.withHttps()) }
        binding.urlLongButton.setOnLongClickListener { copyToClipboard("Long URL", url.longURL) }
        binding.urlLongShareButton.setOnClickListener { shareText(url.longURL) }
        if (url.title.isNotBlank()) {
            binding.urlTitleTextview.text = searchHighlighter(url.title, highlightText)
            binding.urlTitleLayout.isVisible = true
            binding.urlTitleDivider.isVisible = true
        }
        if (url.description.isNotBlank()) {
            binding.urlDescriptionTextview.text = searchHighlighter(url.description, highlightText)
            binding.urlDescriptionLayout.isVisible = true
            binding.urlDescriptionDivider.isVisible = true
        }
        binding.urlAddedTextview.text = searchHighlighter(url.addedFormatMedium, highlightText)
        binding.urlVisitsRefreshButton.setOnClickListener { refreshVisitCount() }
        refreshVisitCount()
        setupBottomNav()
    }

    private fun setupBottomNav() {
        binding.urlBnv.menu.findItem(R.id.url_bnv_analytics)?.isVisible = url.shortURLProvider.getAnalyticsURL(url.shortURL) != null
        binding.urlBnv.menu.findItem(R.id.url_bnv_add_to_fav)?.isVisible = !url.favorite
        binding.urlBnv.menu.findItem(R.id.url_bnv_remove_from_fav)?.isVisible = url.favorite
        binding.urlBnv.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.url_bnv_analytics -> {
                    val analyticsURL = url.shortURLProvider.getAnalyticsURL(url.alias) ?: return@setOnItemSelectedListener false
                    openURL(analyticsURL)
                    true
                }

                R.id.url_bnv_provider_info -> showProviderInfoBottomSheet(url.shortURLProvider).let { true }

                R.id.url_bnv_add_to_fav -> {
                    it.isVisible = false
                    binding.urlBnv.menu.findItem(R.id.url_bnv_remove_from_fav)?.isVisible = true
                    url = url.copy(favorite = true)
                    lifecycleScope.launch { updateURL(url) }
                    true
                }

                R.id.url_bnv_remove_from_fav -> {
                    it.isVisible = false
                    binding.urlBnv.menu.findItem(R.id.url_bnv_add_to_fav)?.isVisible = true
                    url = url.copy(favorite = false)
                    lifecycleScope.launch { updateURL(url) }
                    true
                }

                R.id.url_bnv_delete -> lifecycleScope.launch {
                    AlertDialog.Builder(this@URLActivity)
                        .setTitle(R.string.delete)
                        .setMessage(R.string.delete_url_message)
                        .setPositiveButton(R.string.delete) { _, _ ->
                            lifecycleScope.launch {
                                deleteURL(url)
                                showInAppReviewOrFinish()
                            }
                        }
                        .setNegativeButton(designR.string.oui_des_common_cancel, null)
                        .show()
                }.let { true }

                else -> false
            }
        }
    }
}