package de.lemke.oneurl.ui

import android.app.ActivityOptions
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.util.Pair
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.copyToClipboard
import de.lemke.commonutils.openURL
import de.lemke.commonutils.prepareActivityTransformationBetween
import de.lemke.commonutils.setCustomBackAnimation
import de.lemke.commonutils.toast
import de.lemke.commonutils.transformToActivity
import de.lemke.oneurl.R
import de.lemke.oneurl.databinding.ActivityAddUrlBinding
import de.lemke.oneurl.domain.AddURLUseCase
import de.lemke.oneurl.domain.GenerateQRCodeUseCase
import de.lemke.oneurl.domain.GetURLTitleUseCase
import de.lemke.oneurl.domain.GetURLUseCase
import de.lemke.oneurl.domain.GetUserSettingsUseCase
import de.lemke.oneurl.domain.ObserveUserSettingsUseCase
import de.lemke.oneurl.domain.UpdateUserSettingsUseCase
import de.lemke.oneurl.domain.generateURL.GenerateURLUseCase
import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.domain.model.ShortURLProviderCompanion
import de.lemke.oneurl.domain.model.URL
import de.lemke.oneurl.ui.ProviderActivity.Companion.KEY_SELECT_PROVIDER
import de.lemke.oneurl.ui.ProviderInfoBottomSheet.Companion.showProviderInfoBottomSheet
import de.lemke.oneurl.ui.URLActivity.Companion.KEY_SHORTURL
import dev.oneuiproject.oneui.ktx.hideSoftInput
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import javax.inject.Inject

@AndroidEntryPoint
class AddURLActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddUrlBinding
    private lateinit var selectedShortURLProvider: ShortURLProvider

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var observeUserSettings: ObserveUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    @Inject
    lateinit var generateURL: GenerateURLUseCase

    @Inject
    lateinit var getURLTitle: GetURLTitleUseCase

    @Inject
    lateinit var generateQRCode: GenerateQRCodeUseCase

    @Inject
    lateinit var addURL: AddURLUseCase

    @Inject
    lateinit var getURL: GetURLUseCase


    override fun onCreate(savedInstanceState: Bundle?) {
        prepareActivityTransformationBetween()
        super.onCreate(savedInstanceState)
        binding = ActivityAddUrlBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setCustomBackAnimation(binding.root)
        binding.root.setNavigationButtonOnClickListener {
            hideSoftInput()
            finishAfterTransition()
        }
        initFooterButton()
        lifecycleScope.launch { initViews() }
    }

    private suspend fun initViews() {
        val userSettings = getUserSettings()
        selectedShortURLProvider = ShortURLProviderCompanion.getIfEnabledOrDefault(userSettings.selectedShortURLProvider)
        updateViews()
        val url = intent.getStringExtra("url") ?: userSettings.lastURL
        lifecycleScope.launch { updateUserSettings { it.copy(lastURL = url) } }
        binding.editTextURL.setText(url)
        binding.editTextURL.requestFocus()
        binding.editTextURL.text?.let { binding.editTextURL.setSelection(0, it.length) }
        binding.editTextAlias.setText(userSettings.lastAlias)
        binding.editTextDescription.setText(userSettings.lastDescription)
        binding.editTextURL.addTextChangedListener { text ->
            lifecycleScope.launch { updateUserSettings { it.copy(lastURL = text.toString()) } }
        }
        binding.editTextAlias.addTextChangedListener { text ->
            lifecycleScope.launch { updateUserSettings { it.copy(lastAlias = text.toString()) } }
        }
        binding.editTextDescription.addTextChangedListener { text ->
            lifecycleScope.launch { updateUserSettings { it.copy(lastDescription = text.toString()) } }
        }
        binding.providerSelection.setOnClickListener {
            startActivity(
                Intent(this, ProviderActivity::class.java).putExtra(KEY_SELECT_PROVIDER, true),
                ActivityOptions.makeSceneTransitionAnimation(this, Pair.create(binding.providerSelection, "provider_selection")).toBundle()
            )
        }
        observeUserSettings().flowWithLifecycle(lifecycle).collectLatest {
            if (it.selectedShortURLProvider != selectedShortURLProvider) {
                selectedShortURLProvider = ShortURLProviderCompanion.getIfEnabledOrDefault(it.selectedShortURLProvider)
                updateViews()
            }
        }
    }

    private fun updateViews() {
        binding.providerTitle.text = selectedShortURLProvider.name
        val infoContents = selectedShortURLProvider.getInfoContents(this)
        listOf(
            binding.providerIcon1,
            binding.providerIcon2,
            binding.providerIcon3,
            binding.providerIcon4
        ).forEachIndexed { index, iconView ->
            if (index < infoContents.size) {
                iconView.setImageResource(infoContents[index].icon)
                iconView.isVisible = true
            } else iconView.isVisible = false
        }
        binding.providerIconLayout.setOnClickListener { showProviderInfoBottomSheet(selectedShortURLProvider) }
        binding.textInputLayoutAlias.isVisible = selectedShortURLProvider.aliasConfig != null
        val tipsCardInfo = selectedShortURLProvider.getTipsCardTitleAndInfo(this)
        if (tipsCardInfo != null) {
            binding.addUrlBottomTip.setTitle(tipsCardInfo.first)
            binding.addUrlBottomTip.setSummary(tipsCardInfo.second)
            binding.addUrlBottomTip.setLink(de.lemke.commonutils.R.string.more_information) {
                openURL(selectedShortURLProvider.infoURL)
            }
            binding.addUrlBottomTip.isVisible = true
        } else binding.addUrlBottomTip.isVisible = false
    }

    private fun initFooterButton() {
        if (resources.configuration.screenWidthDp < 360) {
            binding.addUrlFooterButton.layoutParams.width = MATCH_PARENT
        }
        binding.addUrlFooterButton.setOnClickListener { checkAndAddURL() }
    }

    private fun setLoading(messageStringResource: Int) = setLoading(getString(messageStringResource))

    private fun setLoading(message: String?) {
        val loading = !message.isNullOrBlank()
        binding.addUrlFooterProgressText.text = message
        binding.addUrlFooterProgress.isVisible = loading
        binding.addUrlFooterButton.isVisible = !loading
        binding.providerSelection.isEnabled = !loading
        binding.editTextURL.isEnabled = !loading
        binding.editTextAlias.isEnabled = !loading
        binding.editTextDescription.isEnabled = !loading
    }

    private fun checkAndAddURL() {
        if (binding.editTextURL.text.isNullOrBlank()) {
            binding.editTextURL.error = getString(R.string.error_empty_url)
            return
        }
        val alias = (binding.editTextAlias.text ?: "").toString().trim()
        val provider = selectedShortURLProvider
        if (alias.isNotBlank()) {
            provider.aliasConfig?.let {
                if (alias.length < it.minAliasLength) {
                    binding.editTextAlias.error = getString(R.string.error_alias_too_short, it.minAliasLength)
                    return
                }
                if (alias.length > it.maxAliasLength) {
                    binding.editTextAlias.error = getString(R.string.error_alias_too_long, it.maxAliasLength)
                    return
                }
                if (!it.isAliasValid(alias)) {
                    binding.editTextAlias.error = getString(R.string.error_invalid_alias_allowed_characters, it.allowedAliasCharacters)
                    return
                }
            }
        }
        lifecycleScope.launch { checkDuplicates(provider, provider.sanitizeLongURL(binding.editTextURL.text.toString()), alias) }
    }

    private suspend fun checkDuplicates(provider: ShortURLProvider, longURL: String, alias: String) {
        setLoading(R.string.checking_duplicates)
        val existingURLs = getURL(provider, longURL)
        if (existingURLs.isNotEmpty()) {
            if (alias.isBlank()) {
                alreadyShortened(existingURLs.first().shortURL)
                return
            }
            for (url in existingURLs) {
                if (url.shortURL == provider.baseURL + alias) {
                    alreadyShortened(url.shortURL)
                    return
                }
            }
        }
        createURL(provider, longURL, alias)
    }

    private fun alreadyShortened(shortURL: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.error)
            .setMessage(R.string.error_url_already_exists)
            .setNeutralButton(de.lemke.commonutils.R.string.ok, null)
            .setPositiveButton(R.string.to_url) { _: DialogInterface, _: Int ->
                binding.urlInputLayout.transformToActivity(
                    Intent(this, URLActivity::class.java).putExtra(KEY_SHORTURL, shortURL),
                    transitionName = "alreadyShortenedUrlTransition" //transitionNames should be unique within the view hierarchy
                )
            }
            .create()
            .show()
        setLoading(null)
    }

    private suspend fun createURL(provider: ShortURLProvider, longURL: String, alias: String) {
        getURLTitle(longURL) { title ->
            lifecycleScope.launch {
                generateURL(
                    provider = provider,
                    longURL = longURL,
                    alias = alias,
                    setLoadingMessage = { lifecycleScope.launch { setLoading(it) } },
                    errorCallback = {
                        lifecycleScope.launch {
                            AlertDialog.Builder(this@AddURLActivity).apply {
                                setTitle(it.title)
                                setMessage(it.message)
                                setNeutralButton(de.lemke.commonutils.R.string.ok, null)
                                if (it.actionOne != null) {
                                    setPositiveButton(it.actionOne.title) { _: DialogInterface, _: Int -> it.actionOne.action() }
                                }
                                if (it.actionTwo != null) {
                                    setNegativeButton(it.actionTwo.title) { _: DialogInterface, _: Int -> it.actionTwo.action() }
                                }
                                show()
                            }
                            setLoading(null)
                        }
                    },
                    successCallback = { shortURL ->
                        lifecycleScope.launch {
                            MainActivity.scrollToTop = true
                            addURL(
                                URL(
                                    shortURL = shortURL,
                                    longURL = longURL,
                                    shortURLProvider = provider,
                                    qr = generateQRCode(shortURL),
                                    favorite = false,
                                    title = title,
                                    description = binding.editTextDescription.text.toString(),
                                    added = ZonedDateTime.now()
                                )
                            )
                            setLoading(null)
                            if (getUserSettings().autoCopyOnCreate) copyToClipboard(shortURL, title) else toast(R.string.url_added)
                            finishAfterTransition()
                        }
                    },
                )
            }
        }
    }
}