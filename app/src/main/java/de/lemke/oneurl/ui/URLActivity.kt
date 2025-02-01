package de.lemke.oneurl.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.style.UnderlineSpan
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.skydoves.bundler.bundleValue
import com.skydoves.transformationlayout.TransformationAppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.SaveLocation
import de.lemke.commonutils.copyToClipboard
import de.lemke.commonutils.exportBitmap
import de.lemke.commonutils.openURL
import de.lemke.commonutils.saveBitmapToUri
import de.lemke.commonutils.setCustomAnimatedOnBackPressedLogic
import de.lemke.commonutils.setWindowTransparent
import de.lemke.commonutils.shareBitmap
import de.lemke.commonutils.shareText
import de.lemke.commonutils.toast
import de.lemke.oneurl.R
import de.lemke.oneurl.databinding.ActivityUrlBinding
import de.lemke.oneurl.domain.DeleteURLUseCase
import de.lemke.oneurl.domain.GetURLUseCase
import de.lemke.oneurl.domain.GetUserSettingsUseCase
import de.lemke.oneurl.domain.ShowInAppReviewOrFinishUseCase
import de.lemke.oneurl.domain.UpdateURLUseCase
import de.lemke.oneurl.domain.model.URL
import de.lemke.oneurl.domain.urlEncode
import de.lemke.oneurl.domain.withHttps
import dev.oneuiproject.oneui.utils.SearchHighlighter
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class URLActivity : TransformationAppCompatActivity() {
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
    lateinit var showInAppReviewOrFinish: ShowInAppReviewOrFinishUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUrlBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setWindowTransparent(true)
        binding.root.setNavigationButtonOnClickListener { lifecycleScope.launch { showInAppReviewOrFinish(this@URLActivity) } }
        lifecycleScope.launch {
            getURL(bundleValue<String>(KEY_SHORTURL, "")).let {
                if (it == null) {
                    toast(R.string.error_url_not_found)
                    finishAfterTransition()
                    return@launch
                }
                url = it
            }
            saveLocation = getUserSettings().saveLocation
            initViews()
            setCustomAnimatedOnBackPressedLogic(binding.root, showInAppReviewOrFinish.canShowInAppReview()) {
                lifecycleScope.launch { showInAppReviewOrFinish(this@URLActivity) }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.url_toolbar, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.url_toolbar_norton_safe_web -> {
                openURL("https://safeweb.norton.com/report/show?url=${url.longURL.urlEncode()}")
                return true
            }

            R.id.url_toolbar_google_safe_browsing -> {
                openURL("https://transparencyreport.google.com/safe-browsing/search?url=${url.longURL.urlEncode()}")
                return true
            }

            R.id.url_toolbar_link_shield -> {
                openURL("https://linkshieldapi.com/?url=${url.longURL.urlEncode()}")
                return true
            }

            R.id.url_toolbar_malshare -> {
                openURL("https://malshare.com/search.php?query=${url.longURL.urlEncode()}")
                return true
            }

            R.id.url_toolbar_urlhaus -> {
                openURL("https://urlhaus.abuse.ch/browse.php?search=${url.longURL.urlEncode()}")
                return true
            }

            R.id.url_toolbar_kaspersky -> {
                openURL("https://opentip.kaspersky.com/${url.longURL.urlEncode()}/?tab=lookup")
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    @SuppressLint("SetTextI18n")
    private fun refreshVisitCount() {
        binding.urlVisitsRefreshButton.isEnabled = false
        binding.urlVisitsRefreshButton.alpha = 0.5f
        binding.urlVisitsRefreshButton.rotation = 0f
        binding.urlVisitsRefreshButton.animate().rotationBy(-1080f).setDuration(2500).interpolator = AccelerateDecelerateInterpolator()
        url.shortURLProvider.getURLClickCount(this, url) { count ->
            if (count != null) {
                binding.urlVisitsDivider.visibility = View.VISIBLE
                binding.urlVisitsLayout.visibility = View.VISIBLE
                binding.urlVisitsTextview.text = count.toString()
            } else {
                binding.urlVisitsDivider.visibility = View.GONE
                binding.urlVisitsLayout.visibility = View.GONE
            }
            binding.urlVisitsRefreshButton.isEnabled = true
            binding.urlVisitsRefreshButton.alpha = 1f
        }
    }


    private fun initViews() {
        binding.root.setTitle(url.shortURL)
        searchHighlighter = SearchHighlighter(this)
        val highlightText: String = bundleValue(KEY_HIGHLIGHT_TEXT, "")
        refreshVisitCount()
        binding.urlVisitsRefreshButton.setOnClickListener { refreshVisitCount() }
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
            binding.urlTitleLayout.visibility = View.VISIBLE
            binding.urlTitleDivider.visibility = View.VISIBLE
        }
        if (url.description.isNotBlank()) {
            binding.urlDescriptionTextview.text = searchHighlighter(url.description, highlightText)
            binding.urlDescriptionLayout.visibility = View.VISIBLE
            binding.urlDescriptionDivider.visibility = View.VISIBLE
        }
        binding.urlAddedTextview.text = searchHighlighter(url.addedFormatMedium, highlightText)
        binding.urlQrImageview.setImageBitmap(url.qr)
        binding.urlQrImageview.setOnClickListener {
            lifecycleScope.launch {
                QRBottomSheet.newInstance(url.shortURL, url.qr, saveLocation).show(supportFragmentManager, null)
            }
        }
        binding.urlQrImageview.setOnLongClickListener {
            url.qr.copyToClipboard(this, "QR Code", "QRCode.png")
            true
        }
        binding.urlQrSaveButton.setOnClickListener { exportBitmap(saveLocation, url.qr, url.shortURL, exportQRCodeResultLauncher) }
        binding.urlQrShareButton.setOnClickListener { shareBitmap(url.qr, "QRCode.png") }
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
                    return@setOnItemSelectedListener true
                }

                R.id.url_bnv_provider_info -> {
                    ProviderInfoBottomSheet.newInstance(url.shortURLProvider).show(supportFragmentManager, null)
                    return@setOnItemSelectedListener true
                }

                R.id.url_bnv_add_to_fav -> {
                    it.isVisible = false
                    binding.urlBnv.menu.findItem(R.id.url_bnv_remove_from_fav)?.isVisible = true
                    url = url.copy(favorite = true)
                    lifecycleScope.launch { updateURL(url) }
                    return@setOnItemSelectedListener true
                }

                R.id.url_bnv_remove_from_fav -> {
                    it.isVisible = false
                    binding.urlBnv.menu.findItem(R.id.url_bnv_add_to_fav)?.isVisible = true
                    url = url.copy(favorite = false)
                    lifecycleScope.launch { updateURL(url) }
                    return@setOnItemSelectedListener true
                }

                R.id.url_bnv_delete -> {
                    lifecycleScope.launch {
                        AlertDialog.Builder(this@URLActivity)
                            .setTitle(R.string.delete)
                            .setMessage(R.string.delete_url_message)
                            .setPositiveButton(R.string.delete) { _, _ ->
                                lifecycleScope.launch {
                                    deleteURL(url)
                                    showInAppReviewOrFinish(this@URLActivity)
                                }
                            }
                            .setNegativeButton(de.lemke.commonutils.R.string.sesl_cancel, null)
                            .create()
                            .show()
                    }
                    return@setOnItemSelectedListener true
                }

                else -> return@setOnItemSelectedListener false
            }
        }
    }
}