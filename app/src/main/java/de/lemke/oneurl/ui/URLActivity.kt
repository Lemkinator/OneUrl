package de.lemke.oneurl.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.MaterialColors
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.oneurl.R
import de.lemke.oneurl.data.SaveLocation
import de.lemke.oneurl.databinding.ActivityUrlBinding
import de.lemke.oneurl.domain.DeleteURLUseCase
import de.lemke.oneurl.domain.GetURLUseCase
import de.lemke.oneurl.domain.GetUserSettingsUseCase
import de.lemke.oneurl.domain.MakeSectionOfTextBoldUseCase
import de.lemke.oneurl.domain.ShowInAppReviewOrFinishUseCase
import de.lemke.oneurl.domain.UpdateURLUseCase
import de.lemke.oneurl.domain.model.Owovz
import de.lemke.oneurl.domain.model.URL
import de.lemke.oneurl.domain.qr.CopyQRCodeUseCase
import de.lemke.oneurl.domain.qr.ExportQRCodeToSaveLocationUseCase
import de.lemke.oneurl.domain.qr.ExportQRCodeUseCase
import de.lemke.oneurl.domain.qr.ShareQRCodeUseCase
import de.lemke.oneurl.domain.utils.setCustomOnBackPressedLogic
import de.lemke.oneurl.domain.withHttps
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class URLActivity : AppCompatActivity() {
    private lateinit var binding: ActivityUrlBinding
    private lateinit var url: URL
    private lateinit var boldText: String
    private lateinit var saveLocation: SaveLocation
    private lateinit var pickExportFolderActivityResultLauncher: ActivityResultLauncher<Uri>
    private val makeSectionOfTextBold: MakeSectionOfTextBoldUseCase = MakeSectionOfTextBoldUseCase()

    @Inject
    lateinit var getURL: GetURLUseCase

    @Inject
    lateinit var updateURL: UpdateURLUseCase

    @Inject
    lateinit var deleteURL: DeleteURLUseCase

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var exportQRCode: ExportQRCodeUseCase

    @Inject
    lateinit var exportQRCodeToSaveLocation: ExportQRCodeToSaveLocationUseCase

    @Inject
    lateinit var copyQRCode: CopyQRCodeUseCase

    @Inject
    lateinit var shareQRCode: ShareQRCodeUseCase

    @Inject
    lateinit var showInAppReviewOrFinish: ShowInAppReviewOrFinishUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUrlBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.setNavigationButtonOnClickListener { lifecycleScope.launch { showInAppReviewOrFinish(this@URLActivity) } }
        binding.root.tooltipText = getString(R.string.sesl_navigate_up)
        val shortURL = intent.getStringExtra("shortURL")
        boldText = intent.getStringExtra("boldText") ?: ""
        if (shortURL == null) {
            Toast.makeText(this, getString(R.string.error_url_not_found), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        pickExportFolderActivityResultLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            lifecycleScope.launch { exportQRCode(uri, url.qr, url.shortURL) }
        }
        lifecycleScope.launch {
            val nullableURL = getURL(shortURL)
            if (nullableURL == null) {
                Toast.makeText(this@URLActivity, getString(R.string.error_url_not_found), Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            url = nullableURL
            saveLocation = getUserSettings().saveLocation
            binding.root.setTitle(url.shortURL)
            initViews()
            if (showInAppReviewOrFinish.canShowInAppReview()) {
                setCustomOnBackPressedLogic { lifecycleScope.launch { showInAppReviewOrFinish(this@URLActivity) } }
            }
        }
    }

    private fun refreshOwovzVisitCount() {
        (url.shortURLProvider as? Owovz)?.let {
            it.getURLVisitCount(this, url.shortURL) { visitCount ->
                if (visitCount != null) {
                    binding.urlVisitsDivider.visibility = View.VISIBLE
                    binding.urlVisitsLayout.visibility = View.VISIBLE
                    binding.urlVisitsTextview.text = visitCount.toString()
                } else {
                    binding.urlVisitsDivider.visibility = View.GONE
                    binding.urlVisitsLayout.visibility = View.GONE
                }
            }
        }
    }


    private fun initViews() {
        refreshOwovzVisitCount()
        binding.urlVisitsRefreshButton.setOnClickListener {
            refreshOwovzVisitCount()
            binding.urlVisitsRefreshButton.isEnabled = false
            binding.urlVisitsRefreshButton.alpha = 0.5f
            binding.urlVisitsRefreshButton.animate().rotationBy(-720f).setDuration(1500)
                .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator()).withEndAction {
                binding.urlVisitsRefreshButton.isEnabled = true
                binding.urlVisitsRefreshButton.alpha = 1f
            }

        }
        val color = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, this.getColor(R.color.primary_color_themed))
        val shortURL = with(makeSectionOfTextBold(url.shortURL, boldText, color)) {
            setSpan(android.text.style.UnderlineSpan(), 0, url.shortURL.length, 0)
            this
        }
        binding.urlShortButton.text = shortURL
        binding.urlShortButton.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url.shortURL.withHttps())))
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("URLActivity", "Error: ${e.message}")
                Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
            }
        }
        val longURL = with(makeSectionOfTextBold(url.longURL, boldText, color)) {
            setSpan(android.text.style.UnderlineSpan(), 0, url.longURL.length, 0)
            this
        }
        binding.urlShortCopyButton.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("short-url", url.shortURL)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
        }
        binding.urlShortShareButton.setOnClickListener {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, url.shortURL)
                type = "text/plain"
            }
            startActivity(Intent.createChooser(sendIntent, null))
        }
        binding.urlLongButton.text = longURL
        binding.urlLongButton.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(longURL.toString().withHttps())))
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("URLActivity", "Error: ${e.message}")
                Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
            }
        }
        binding.urlLongCopyButton.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("long-url", url.longURL)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
        }
        binding.urlLongShareButton.setOnClickListener {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, url.longURL)
                type = "text/plain"
            }
            startActivity(Intent.createChooser(sendIntent, null))
        }
        if (url.title.isNotBlank()) {
            binding.urlTitleTextview.text = makeSectionOfTextBold(url.title, boldText, color)
            binding.urlTitleLayout.visibility = View.VISIBLE
            binding.urlTitleDivider.visibility = View.VISIBLE
        }
        if (url.description.isNotBlank()) {
            binding.urlDescriptionTextview.text = makeSectionOfTextBold(url.description, boldText, color)
            binding.urlDescriptionLayout.visibility = View.VISIBLE
            binding.urlDescriptionDivider.visibility = View.VISIBLE
        }
        binding.urlAddedTextview.text = makeSectionOfTextBold(url.addedFormatMedium, boldText, color)
        binding.urlQrImageview.setImageBitmap(url.qr)
        binding.urlQrCopyButton.setOnClickListener { copyQRCode(url.qr) }
        binding.urlQrSaveButton.setOnClickListener {
            if (saveLocation == SaveLocation.CUSTOM) {
                pickExportFolderActivityResultLauncher.launch(Uri.fromFile(File(Environment.getExternalStorageDirectory().absolutePath)))
            } else {
                exportQRCodeToSaveLocation(saveLocation, url.qr, url.shortURL)
            }
        }
        binding.urlQrShareButton.setOnClickListener { shareQRCode(url.qr) }
        initBNV()
    }

    private fun initBNV() {
        binding.urlBnv.menu.findItem(R.id.url_bnv_analytics)?.isVisible = url.shortURLProvider.getAnalyticsURL(url.shortURL) != null
        binding.urlBnv.menu.findItem(R.id.url_bnv_add_to_fav)?.isVisible = !url.favorite
        binding.urlBnv.menu.findItem(R.id.url_bnv_remove_from_fav)?.isVisible = url.favorite
        binding.urlBnv.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.url_bnv_analytics -> {
                    try {
                        val analyticsURL = url.shortURLProvider.getAnalyticsURL(url.alias) ?: return@setOnItemSelectedListener false
                        Log.d("URLActivity", "analyticsURL: $analyticsURL")
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(analyticsURL)))
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(this, getString(R.string.error), Toast.LENGTH_SHORT).show()
                    }
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
                            .setNegativeButton(R.string.sesl_cancel, null)
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