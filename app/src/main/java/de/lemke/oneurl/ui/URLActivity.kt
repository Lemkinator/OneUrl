package de.lemke.oneurl.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
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
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.MaterialColors
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.oneurl.R
import de.lemke.oneurl.databinding.ActivityUrlBinding
import de.lemke.oneurl.domain.DeleteURLUseCase
import de.lemke.oneurl.domain.GetURLUseCase
import de.lemke.oneurl.domain.GetUserSettingsUseCase
import de.lemke.oneurl.domain.MakeSectionOfTextBoldUseCase
import de.lemke.oneurl.domain.UpdateURLUseCase
import de.lemke.oneurl.domain.UpdateUserSettingsUseCase
import de.lemke.oneurl.domain.model.URL
import de.lemke.oneurl.domain.utils.setCustomOnBackPressedLogic
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class URLActivity : AppCompatActivity() {
    private lateinit var binding: ActivityUrlBinding
    private lateinit var url: URL
    private lateinit var boldText: String
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
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUrlBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.setNavigationButtonOnClickListener { showInAppReviewOrFinish() }
        binding.root.tooltipText = getString(R.string.sesl_navigate_up)
        val shortURL = intent.getStringExtra("shortURL")
        boldText = intent.getStringExtra("boldText") ?: ""
        if (shortURL == null) {
            Toast.makeText(this, getString(R.string.url_not_found), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        pickExportFolderActivityResultLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri == null)
                Toast.makeText(this@URLActivity, getString(R.string.error_no_folder_selected), Toast.LENGTH_LONG).show()
            else lifecycleScope.launch { exportQR(uri) }
        }
        lifecycleScope.launch {
            val nullableURL = getURL(shortURL)
            if (nullableURL == null) {
                Toast.makeText(this@URLActivity, getString(R.string.url_not_found), Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            url = nullableURL
            binding.root.setTitle(url.shortURL)
            initViews()

        }
        setCustomOnBackPressedLogic { showInAppReviewOrFinish() }
    }

    private fun showInAppReviewOrFinish() {
        lifecycleScope.launch {
            try {
                val lastInAppReviewRequest = getUserSettings().lastInAppReviewRequest
                val daysSinceLastRequest = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - lastInAppReviewRequest)
                if (daysSinceLastRequest < 14) {
                    finishAfterTransition()
                    return@launch
                }
                updateUserSettings { it.copy(lastInAppReviewRequest = System.currentTimeMillis()) }
                val manager = ReviewManagerFactory.create(this@URLActivity)
                //val manager = FakeReviewManager(context);
                val request = manager.requestReviewFlow()
                request.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val reviewInfo = task.result
                        val flow = manager.launchReviewFlow(this@URLActivity, reviewInfo)
                        flow.addOnCompleteListener { finishAfterTransition() }
                    } else {
                        // There was some problem, log or handle the error code.
                        Log.e("InAppReview", "Review task failed: ${task.exception?.message}")
                        finishAfterTransition()
                    }
                }
            } catch (e: Exception) {
                Log.e("InAppReview", "Error: ${e.message}")
                finishAfterTransition()
            }
        }
    }

    private fun initViews() {
        val color = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, this.getColor(R.color.primary_color_themed))
        //underline text
        val shortURL = with(makeSectionOfTextBold(url.shortURL, boldText, color)) {
            setSpan(android.text.style.UnderlineSpan(), 0, url.shortURL.length, 0)
            this
        }
        binding.urlShortButton.text = shortURL
        binding.urlShortButton.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url.shortURL)))
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
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(longURL.toString())))
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
        if (url.description.isNotBlank()) {
            binding.urlDescriptionTextview.text = makeSectionOfTextBold(url.description, boldText, color)
            binding.urlDescriptionTextview.visibility = View.VISIBLE
        }
        binding.urlAddedTextview.text = makeSectionOfTextBold(url.addedFormatMedium, boldText, color)
        binding.urlQrImageview.setImageBitmap(url.qr)
        binding.urlQrCopyButton.setOnClickListener {
            val cacheFile = File(cacheDir, "qr.png")
            url.qr.compress(Bitmap.CompressFormat.PNG, 100, cacheFile.outputStream())
            val uri = FileProvider.getUriForFile(this, "de.lemke.oneurl.fileprovider", cacheFile)
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newUri(contentResolver, "qr-code", uri)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
        }
        binding.urlQrShareButton.setOnClickListener {
            val cacheFile = File(cacheDir, "qr.png")
            url.qr.compress(Bitmap.CompressFormat.PNG, 100, cacheFile.outputStream())
            val uri = FileProvider.getUriForFile(this, "de.lemke.oneurl.fileprovider", cacheFile)
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, uri)
                type = "image/png"
            }
            startActivity(Intent.createChooser(sendIntent, null))
        }
        initBNV()
    }

    private fun exportQR(uri: Uri) {
        val timestamp = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.GERMANY).format(Date())
        val pngFile = DocumentFile.fromTreeUri(this, uri)!!.createFile("image/png", "${url.shortURL}_$timestamp")
        url.qr.compress(Bitmap.CompressFormat.PNG, 100, contentResolver.openOutputStream(pngFile!!.uri)!!)
        Toast.makeText(this, R.string.qr_saved, Toast.LENGTH_LONG).show()
    }

    private fun initBNV() {
        binding.urlBnv.menu.findItem(R.id.url_bnv_add_to_fav).isVisible = !url.favorite
        binding.urlBnv.menu.findItem(R.id.url_bnv_remove_from_fav).isVisible = url.favorite
        binding.urlBnv.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.url_bnv_delete -> {
                    lifecycleScope.launch {
                        AlertDialog.Builder(this@URLActivity)
                            .setTitle(R.string.delete)
                            .setMessage(R.string.delete_url_message)
                            .setPositiveButton(R.string.delete) { _, _ ->
                                lifecycleScope.launch {
                                    deleteURL(url)
                                    showInAppReviewOrFinish()
                                }
                            }
                            .setNegativeButton(R.string.sesl_cancel, null)
                            .create()
                            .show()
                    }
                    return@setOnItemSelectedListener true
                }

                R.id.url_bnv_add_to_fav -> {
                    it.isVisible = false
                    binding.urlBnv.menu.findItem(R.id.url_bnv_remove_from_fav).isVisible = true
                    url = url.copy(favorite = true)
                    lifecycleScope.launch { updateURL(url) }
                    return@setOnItemSelectedListener true
                }

                R.id.url_bnv_remove_from_fav -> {
                    it.isVisible = false
                    binding.urlBnv.menu.findItem(R.id.url_bnv_add_to_fav).isVisible = true
                    url = url.copy(favorite = false)
                    lifecycleScope.launch { updateURL(url) }
                    return@setOnItemSelectedListener true
                }

                R.id.url_bnv_save_as_image -> {
                    pickExportFolderActivityResultLauncher.launch(Uri.fromFile(File(Environment.getExternalStorageDirectory().absolutePath)))
                    return@setOnItemSelectedListener true
                }

                else -> return@setOnItemSelectedListener false
            }
        }
    }
}