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
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.ui.utils.collectEvents
import de.lemke.commonutils.ui.utils.collectState
import de.lemke.commonutils.ui.utils.copyToClipboard
import de.lemke.commonutils.ui.utils.exportBitmap
import de.lemke.commonutils.ui.utils.openURL
import de.lemke.commonutils.ui.utils.prepareActivityTransformationTo
import de.lemke.commonutils.ui.utils.saveBitmapToUri
import de.lemke.commonutils.ui.utils.setCustomBackAnimation
import de.lemke.commonutils.ui.utils.setWindowTransparent
import de.lemke.commonutils.ui.utils.shareBitmap
import de.lemke.commonutils.ui.utils.shareText
import de.lemke.commonutils.ui.utils.showInAppReviewOrFinish
import de.lemke.commonutils.ui.utils.toast
import de.lemke.commonutils.ui.utils.urlEncode
import de.lemke.commonutils.ui.utils.withHttps
import de.lemke.oneurl.R
import de.lemke.oneurl.databinding.ActivityUrlBinding
import de.lemke.oneurl.domain.model.URL
import de.lemke.oneurl.ui.ProviderInfoBottomSheet.Companion.showProviderInfoBottomSheet
import de.lemke.oneurl.ui.QRBottomSheet.Companion.createQRBottomSheet
import dev.oneuiproject.oneui.utils.SearchHighlighter
import java.text.NumberFormat
import javax.inject.Inject
import de.lemke.commonutils.R as commonutilsR
import dev.oneuiproject.oneui.design.R as designR

@AndroidEntryPoint
class URLActivity : AppCompatActivity() {
    companion object {
        const val KEY_SHORTURL = "key_shorturl"
        const val KEY_HIGHLIGHT_TEXT = "key_highlight_text"
    }

    @Inject
    lateinit var settings: SettingsRepository

    private lateinit var binding: ActivityUrlBinding
    private val viewModel: URLViewModel by viewModels()
    private lateinit var searchHighlighter: SearchHighlighter
    private var lastBoundShortURL: String? = null
    private val exportQRCodeResultLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                viewModel.state.value.url
                    ?.qr
                    ?.let { saveBitmapToUri(result.data?.data, it) }
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
        val longURL =
            viewModel.state.value.url
                ?.longURL ?: return false
        val urlScanTemplate = urlScanTemplateFor(item.itemId) ?: return super.onOptionsItemSelected(item)
        openURL(urlScanTemplate(longURL.urlEncode()))
        return true
    }

    private fun urlScanTemplateFor(itemId: Int): ((encodedURL: String) -> String)? =
        when (itemId) {
            R.id.url_toolbar_norton_safe_web -> { encoded -> "https://safeweb.norton.com/report/show?url=$encoded" }

            R.id.url_toolbar_google_safe_browsing -> { encoded ->
                "https://transparencyreport.google.com/safe-browsing/search?url=$encoded"
            }

            R.id.url_toolbar_link_shield -> { encoded -> "https://linkshieldapi.com/?url=$encoded" }

            R.id.url_toolbar_malshare -> { encoded -> "https://malshare.com/search.php?query=$encoded" }

            R.id.url_toolbar_urlhaus -> { encoded -> "https://urlhaus.abuse.ch/browse.php?search=$encoded" }

            R.id.url_toolbar_kaspersky -> { encoded -> "https://opentip.kaspersky.com/$encoded/?tab=lookup" }

            else -> null
        }

    private fun collectState() =
        collectState(viewModel.state) { state ->
            if (state.isLoading || state.url == null) return@collectState
            val url = state.url
            if (url.shortURL != lastBoundShortURL) {
                lastBoundShortURL = url.shortURL
                bindURL(url)
            }
            updateFavoriteAndVisitViews(state, url)
        }

    private fun bindURL(url: URL) {
        val highlightText: String = bundleValue(KEY_HIGHLIGHT_TEXT, "")
        binding.root.setTitle(url.shortURL)
        binding.urlQrImageview.setImageBitmap(url.qr)
        binding.urlQrImageview.setOnClickListener {
            createQRBottomSheet(url.shortURL, url.qr, settings.imageSaveLocation).show(supportFragmentManager, null)
        }
        binding.urlQrImageview.setOnLongClickListener {
            url.qr
                .copyToClipboard(
                    this@URLActivity,
                    "QR Code",
                    "QRCode.png",
                ).let { true }
        }
        binding.urlQrSaveButton.setOnClickListener {
            exportBitmap(
                settings.imageSaveLocation,
                url.qr,
                url.shortURL,
                exportQRCodeResultLauncher,
            )
        }
        binding.urlQrShareButton.setOnClickListener { shareBitmap(url.qr, "QRCode.png") }
        binding.urlShortButton.text =
            searchHighlighter(url.shortURL, highlightText).apply {
                setSpan(UnderlineSpan(), 0, url.shortURL.length, 0)
            }
        binding.urlShortButton.setOnClickListener { openURL(url.shortURL.withHttps()) }
        binding.urlShortButton.setOnLongClickListener { copyToClipboard(url.shortURL, "Short URL") }
        binding.urlShortShareButton.setOnClickListener { shareText(url.shortURL) }
        binding.urlLongButton.text =
            searchHighlighter(url.longURL, highlightText).apply {
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
        binding.urlBnv.menu
            .findItem(R.id.url_bnv_analytics)
            ?.isVisible = url.shortURLProvider.getAnalyticsURL(url.alias) != null
        binding.urlBnv.setOnItemSelectedListener { item -> handleBnvItemSelected(item, url) }
        setCustomBackAnimation(binding.root, showInAppReviewIfPossible = true)
    }

    private fun handleBnvItemSelected(
        item: MenuItem,
        url: URL,
    ): Boolean =
        when (item.itemId) {
            R.id.url_bnv_analytics -> {
                val analyticsURL = url.shortURLProvider.getAnalyticsURL(url.alias) ?: return false
                openURL(analyticsURL)
                true
            }

            R.id.url_bnv_provider_info -> {
                showProviderInfoBottomSheet(url.shortURLProvider).let { true }
            }

            R.id.url_bnv_add_to_fav -> {
                viewModel.toggleFavorite().let { true }
            }

            R.id.url_bnv_remove_from_fav -> {
                viewModel.toggleFavorite().let { true }
            }

            R.id.url_bnv_delete -> {
                AlertDialog
                    .Builder(this@URLActivity)
                    .setTitle(commonutilsR.string.commonutils_delete)
                    .setMessage(R.string.delete_url_message)
                    .setPositiveButton(commonutilsR.string.commonutils_delete) { _, _ -> viewModel.delete() }
                    .setNegativeButton(designR.string.oui_des_common_cancel, null)
                    .show()
                true
            }

            else -> {
                false
            }
        }

    private fun updateFavoriteAndVisitViews(
        state: UrlDetailUiState,
        url: URL,
    ) {
        binding.urlBnv.menu
            .findItem(R.id.url_bnv_add_to_fav)
            ?.isVisible = !url.favorite
        binding.urlBnv.menu
            .findItem(R.id.url_bnv_remove_from_fav)
            ?.isVisible = url.favorite
        val visitCount = state.visitCount
        binding.urlVisitsDivider.isVisible = visitCount != null
        binding.urlVisitsLayout.isVisible = visitCount != null
        if (visitCount != null) {
            binding.urlVisitsTextview.text = NumberFormat.getIntegerInstance(resources.configuration.locales[0]).format(visitCount)
        }
        renderVisitCountRefresh(state.isRefreshingVisits)
    }

    @SuppressLint("SetTextI18n")
    private fun renderVisitCountRefresh(isRefreshing: Boolean) {
        binding.urlVisitsRefreshButton.isEnabled = !isRefreshing
        binding.urlVisitsRefreshButton.alpha = if (isRefreshing) 0.5f else 1f
        if (isRefreshing) {
            binding.urlVisitsRefreshButton.rotation = 0f
            binding.urlVisitsRefreshButton
                .animate()
                .rotationBy(-1080f)
                .setDuration(2500)
                .interpolator = AccelerateDecelerateInterpolator()
        }
    }

    private fun collectEvents() =
        collectEvents(viewModel.events) { event: UrlDetailEvent ->
            when (event) {
                is UrlDetailEvent.NotFound -> {
                    toast(R.string.error_url_not_found)
                    finishAfterTransition()
                }

                is UrlDetailEvent.Deleted -> {
                    showInAppReviewOrFinish()
                }
            }
        }
}
