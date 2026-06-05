package de.lemke.oneurl.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.style.UnderlineSpan
import android.view.Menu
import android.view.MenuItem
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.skydoves.bundler.bundleValue
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.collectEvents
import de.lemke.commonutils.collectState
import de.lemke.commonutils.copyToClipboard
import de.lemke.commonutils.data.commonUtilsSettings
import de.lemke.commonutils.exportBitmap
import de.lemke.commonutils.openURL
import de.lemke.commonutils.prepareActivityTransformationTo
import de.lemke.commonutils.saveBitmapToUri
import de.lemke.commonutils.setCustomBackAnimation
import de.lemke.commonutils.setWindowTransparent
import de.lemke.commonutils.shareBitmap
import de.lemke.commonutils.shareText
import de.lemke.commonutils.showInAppReviewOrFinish
import de.lemke.commonutils.toast
import de.lemke.commonutils.urlEncode
import de.lemke.commonutils.withHttps
import de.lemke.oneurl.R
import de.lemke.oneurl.databinding.ActivityUrlBinding
import de.lemke.oneurl.domain.model.URL as DomainURL
import de.lemke.oneurl.ui.ProviderInfoBottomSheet.Companion.showProviderInfoBottomSheet
import de.lemke.oneurl.ui.QRBottomSheet.Companion.createQRBottomSheet
import dev.oneuiproject.oneui.utils.SearchHighlighter
import de.lemke.commonutils.R as commonutilsR
import dev.oneuiproject.oneui.design.R as designR

@AndroidEntryPoint
class URLActivity : AppCompatActivity() {
    companion object {
        const val KEY_SHORTURL = "key_shorturl"
        const val KEY_HIGHLIGHT_TEXT = "key_highlight_text"
    }

    private lateinit var binding: ActivityUrlBinding
    private val viewModel: URLViewModel by viewModels()
    private lateinit var searchHighlighter: SearchHighlighter
    private var lastBoundUrl: DomainURL? = null
    private val exportQRCodeResultLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.state.value.url?.qr?.let { saveBitmapToUri(result.data?.data, it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prepareActivityTransformationTo()
        super.onCreate(savedInstanceState)
        binding = ActivityUrlBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setWindowTransparent(true)
        searchHighlighter = SearchHighlighter(this)
        collectState()
        collectEvents()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean = menuInflater.inflate(R.menu.url_toolbar, menu).let { true }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val longURL = viewModel.state.value.url?.longURL ?: return false
        return when (item.itemId) {
            R.id.url_toolbar_norton_safe_web -> openURL("https://safeweb.norton.com/report/show?url=${longURL.urlEncode()}").let { true }
            R.id.url_toolbar_google_safe_browsing -> openURL("https://transparencyreport.google.com/safe-browsing/search?url=${longURL.urlEncode()}").let { true }
            R.id.url_toolbar_link_shield -> openURL("https://linkshieldapi.com/?url=${longURL.urlEncode()}").let { true }
            R.id.url_toolbar_malshare -> openURL("https://malshare.com/search.php?query=${longURL.urlEncode()}").let { true }
            R.id.url_toolbar_urlhaus -> openURL("https://urlhaus.abuse.ch/browse.php?search=${longURL.urlEncode()}").let { true }
            R.id.url_toolbar_kaspersky -> openURL("https://opentip.kaspersky.com/${longURL.urlEncode()}/?tab=lookup").let { true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun collectState() = collectState(viewModel.state) { state ->
        if (state.isLoading || state.url == null) return@collectState
        val url = state.url
        if (url != lastBoundUrl) {
            lastBoundUrl = url
            val highlightText: String = bundleValue(KEY_HIGHLIGHT_TEXT, "")
            binding.root.setTitle(url.shortURL)
            binding.urlQrImageview.setImageBitmap(url.qr)
            binding.urlQrImageview.setOnClickListener {
                createQRBottomSheet(url.shortURL, url.qr, commonUtilsSettings.imageSaveLocation).show(supportFragmentManager, null)
            }
            binding.urlQrImageview.setOnLongClickListener { url.qr.copyToClipboard(this@URLActivity, "QR Code", "QRCode.png").let { true } }
            binding.urlQrSaveButton.setOnClickListener { exportBitmap(commonUtilsSettings.imageSaveLocation, url.qr, url.shortURL, exportQRCodeResultLauncher) }
            binding.urlQrShareButton.setOnClickListener { shareBitmap(url.qr, "QRCode.png") }
            binding.urlShortButton.text = searchHighlighter(url.shortURL, highlightText).apply {
                setSpan(UnderlineSpan(), 0, url.shortURL.length, 0)
            }
            binding.urlShortButton.setOnClickListener { openURL(url.shortURL.withHttps()) }
            binding.urlShortButton.setOnLongClickListener { copyToClipboard(url.shortURL, "Short URL") }
            binding.urlShortShareButton.setOnClickListener { shareText(url.shortURL) }
            binding.urlLongButton.text = searchHighlighter(url.longURL, highlightText).apply {
                setSpan(UnderlineSpan(), 0, url.longURL.length, 0)
            }
            binding.urlLongButton.setOnClickListener { openURL(url.longURL.withHttps()) }
            binding.urlLongButton.setOnLongClickListener { copyToClipboard(url.longURL, "Long URL") }
            binding.urlLongShareButton.setOnClickListener { shareText(url.longURL) }
            binding.urlTitleLayout.isVisible = url.title.isNotBlank()
            binding.urlTitleDivider.isVisible = url.title.isNotBlank()
            if (url.title.isNotBlank()) binding.urlTitleTextview.text = searchHighlighter(url.title, highlightText)
            binding.urlDescriptionLayout.isVisible = url.description.isNotBlank()
            binding.urlDescriptionDivider.isVisible = url.description.isNotBlank()
            if (url.description.isNotBlank()) binding.urlDescriptionTextview.text = searchHighlighter(url.description, highlightText)
            binding.urlAddedTextview.text = searchHighlighter(url.addedFormatMedium, highlightText)
            binding.urlVisitsRefreshButton.setOnClickListener { viewModel.refreshVisitCount() }
            binding.bottomTipView.setOnLinkClickListener { copyToClipboard(url.shortURL, "Short URL") }
            binding.urlBnv.menu.findItem(R.id.url_bnv_analytics)?.isVisible = url.shortURLProvider.getAnalyticsURL(url.alias) != null
            binding.urlBnv.menu.findItem(R.id.url_bnv_add_to_fav)?.isVisible = !url.favorite
            binding.urlBnv.menu.findItem(R.id.url_bnv_remove_from_fav)?.isVisible = url.favorite
            binding.urlBnv.setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.url_bnv_analytics -> {
                        val analyticsURL = url.shortURLProvider.getAnalyticsURL(url.alias) ?: return@setOnItemSelectedListener false
                        openURL(analyticsURL)
                        true
                    }
                    R.id.url_bnv_provider_info -> showProviderInfoBottomSheet(url.shortURLProvider).let { true }
                    R.id.url_bnv_add_to_fav -> viewModel.toggleFavorite().let { true }
                    R.id.url_bnv_remove_from_fav -> viewModel.toggleFavorite().let { true }
                    R.id.url_bnv_delete -> {
                        AlertDialog.Builder(this@URLActivity)
                            .setTitle(commonutilsR.string.commonutils_delete)
                            .setMessage(R.string.delete_url_message)
                            .setPositiveButton(commonutilsR.string.commonutils_delete) { _, _ -> viewModel.delete() }
                            .setNegativeButton(designR.string.oui_des_common_cancel, null)
                            .show()
                        true
                    }
                    else -> false
                }
            }
            setCustomBackAnimation(binding.root, showInAppReviewIfPossible = true)
        }

        val visitCount = state.visitCount
        binding.urlVisitsDivider.isVisible = visitCount != null
        binding.urlVisitsLayout.isVisible = visitCount != null
        if (visitCount != null) binding.urlVisitsTextview.text = visitCount.toString()
        renderVisitCountRefresh(state.isRefreshingVisits)
    }

    @SuppressLint("SetTextI18n")
    private fun renderVisitCountRefresh(isRefreshing: Boolean) {
        binding.urlVisitsRefreshButton.isEnabled = !isRefreshing
        binding.urlVisitsRefreshButton.alpha = if (isRefreshing) 0.5f else 1f
        if (isRefreshing) {
            binding.urlVisitsRefreshButton.rotation = 0f
            binding.urlVisitsRefreshButton.animate().rotationBy(-1080f).setDuration(2500).interpolator = AccelerateDecelerateInterpolator()
        }
    }

    private fun collectEvents() = collectEvents(viewModel.events) { event: UrlDetailEvent ->
        when (event) {
            is UrlDetailEvent.NotFound -> {
                toast(R.string.error_url_not_found)
                finishAfterTransition()
            }
            is UrlDetailEvent.Deleted -> showInAppReviewOrFinish()
        }
    }
}
