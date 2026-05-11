package de.lemke.oneurl.ui

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Pair
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.copyToClipboard
import de.lemke.commonutils.openURL
import de.lemke.commonutils.prepareActivityTransformationBetween
import de.lemke.commonutils.setCustomBackAnimation
import de.lemke.commonutils.toast
import de.lemke.commonutils.transformToActivity
import de.lemke.oneurl.R
import de.lemke.oneurl.databinding.ActivityAddUrlBinding
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.ui.ProviderActivity.Companion.KEY_SELECT_PROVIDER
import de.lemke.oneurl.ui.ProviderInfoBottomSheet.Companion.showProviderInfoBottomSheet
import de.lemke.oneurl.ui.URLActivity.Companion.KEY_SHORTURL
import dev.oneuiproject.oneui.ktx.hideSoftInput
import kotlinx.coroutines.launch
import de.lemke.commonutils.R as commonutilsR

@AndroidEntryPoint
class AddURLActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddUrlBinding
    private val viewModel: AddURLViewModel by viewModels()
    private var isInitialized = false

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
        collectState()
        collectEvents()
    }

    private fun collectState() = lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.state.collect { state ->
                if (state.isLoading && !isInitialized) return@collect
                if (!isInitialized) {
                    isInitialized = true
                    initViews(state)
                }
                updateProvider(state.selectedProvider)
                renderLoadingState(state)
            }
        }
    }

    private fun collectEvents() = lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is AddUrlEvent.AlreadyShortened -> showAlreadyShortenedDialog(event.shortURL)
                    is AddUrlEvent.Error -> showErrorDialog(event.error)
                    is AddUrlEvent.CopyAndFinish -> {
                        copyToClipboard(event.shortURL, event.title)
                        finishAfterTransition()
                    }

                    AddUrlEvent.Saved -> {
                        toast(R.string.url_added)
                        finishAfterTransition()
                    }
                }
            }
        }
    }

    private fun initViews(state: AddUrlUiState) {
        binding.editTextURL.setText(state.initialURL)
        binding.editTextURL.requestFocus()
        binding.editTextURL.text?.let { binding.editTextURL.setSelection(0, it.length) }
        binding.editTextAlias.setText(state.initialAlias)
        binding.editTextDescription.setText(state.initialDescription)
        binding.editTextURL.addTextChangedListener { text -> viewModel.onLongURLChanged(text.toString()) }
        binding.editTextAlias.addTextChangedListener { text -> viewModel.onAliasChanged(text.toString()) }
        binding.editTextDescription.addTextChangedListener { text -> viewModel.onDescriptionChanged(text.toString()) }
        binding.providerSelection.setOnClickListener {
            startActivity(
                Intent(this, ProviderActivity::class.java).putExtra(KEY_SELECT_PROVIDER, true),
                ActivityOptions.makeSceneTransitionAnimation(this, Pair.create(binding.providerSelection, "provider_selection")).toBundle()
            )
        }
    }

    private fun updateProvider(provider: ShortURLProvider) {
        binding.providerTitle.text = provider.name
        val infoContents = provider.getInfoContents(this)
        listOf(binding.providerIcon1, binding.providerIcon2, binding.providerIcon3, binding.providerIcon4)
            .forEachIndexed { index, iconView ->
                if (index < infoContents.size) {
                    iconView.setImageResource(infoContents[index].icon)
                    iconView.isVisible = true
                } else iconView.isVisible = false
            }
        binding.providerIconLayout.setOnClickListener { showProviderInfoBottomSheet(provider) }
        binding.textInputLayoutAlias.isVisible = provider.aliasConfig != null
        val tipsCardInfo = provider.getTipsCardTitleAndInfo(this)
        if (tipsCardInfo != null) {
            binding.addUrlBottomTip.setTitle(tipsCardInfo.first)
            binding.addUrlBottomTip.setSummary(tipsCardInfo.second)
            binding.addUrlBottomTip.setLink(commonutilsR.string.commonutils_more_information) { openURL(provider.infoURL) }
            binding.addUrlBottomTip.isVisible = true
        } else binding.addUrlBottomTip.isVisible = false
    }

    private fun renderLoadingState(state: AddUrlUiState) {
        val loading = state.isLoading
        binding.addUrlFooterProgressText.text = if (loading && state.loadingMessageRes != 0) getString(state.loadingMessageRes) else ""
        binding.addUrlFooterProgress.isVisible = loading
        binding.addUrlFooterButton.isVisible = !loading
        binding.providerSelection.isEnabled = !loading
        binding.editTextURL.isEnabled = !loading
        binding.editTextAlias.isEnabled = !loading
        binding.editTextDescription.isEnabled = !loading
    }

    private fun initFooterButton() {
        if (resources.configuration.screenWidthDp < 360) {
            binding.addUrlFooterButton.layoutParams.width = MATCH_PARENT
        }
        binding.addUrlFooterButton.setOnClickListener { submit() }
    }

    private fun submit() {
        val longURLRaw = binding.editTextURL.text.toString()
        if (longURLRaw.isBlank()) {
            binding.editTextURL.error = getString(R.string.error_empty_url)
            return
        }
        val alias = binding.editTextAlias.text?.toString()?.trim() ?: ""
        val provider = viewModel.state.value.selectedProvider
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
        viewModel.submit(longURLRaw, alias, binding.editTextDescription.text?.toString() ?: "")
    }

    private fun showAlreadyShortenedDialog(shortURL: String) {
        AlertDialog.Builder(this)
            .setTitle(commonutilsR.string.commonutils_error)
            .setMessage(R.string.error_url_already_exists)
            .setNeutralButton(commonutilsR.string.commonutils_ok, null)
            .setPositiveButton(R.string.to_url) { _, _ ->
                binding.urlInputLayout.transformToActivity(
                    Intent(this, URLActivity::class.java).putExtra(KEY_SHORTURL, shortURL),
                    transitionName = "alreadyShortenedUrlTransition"
                )
            }
            .show()
    }

    private fun showErrorDialog(error: GenerateURLError) {
        AlertDialog.Builder(this)
            .setNeutralButton(commonutilsR.string.commonutils_ok, null)
            .apply {
                when (error) {
                    GenerateURLError.NoInternet -> {
                        setTitle(R.string.no_internet)
                        setMessage(R.string.no_internet_text)
                        setPositiveButton(commonutilsR.string.commonutils_settings) { _, _ -> startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
                    }

                    is GenerateURLError.BlacklistedURL -> {
                        setTitle(commonutilsR.string.commonutils_error)
                        setMessage(error.message ?: getString(R.string.error_urlhaus_default))
                        if (error.urlhausLink != null) setPositiveButton("URLhaus") { _, _ -> openURL(error.urlhausLink) }
                        if (error.virustotalLink != null) setNegativeButton("VirusTotal") { _, _ -> openURL(error.virustotalLink) }
                    }

                    is GenerateURLError.ServiceTemporarilyUnavailable -> {
                        setTitle(R.string.error_service_unavailable)
                        setMessage(R.string.error_service_unavailable_text)
                        setPositiveButton(commonutilsR.string.commonutils_more_information) { _, _ -> openURL(error.providerBaseURL) }
                    }

                    is GenerateURLError.Custom -> {
                        setTitle(error.customTitle ?: getString(commonutilsR.string.commonutils_error))
                        setMessage(error.customMessage)
                    }

                    is GenerateURLError.Unknown -> {
                        setTitle(commonutilsR.string.commonutils_error)
                        setMessage(if (error.statusCode != null) "Error ${error.statusCode}" else getString(commonutilsR.string.commonutils_error))
                    }

                    GenerateURLError.AliasAlreadyExists -> {
                        setTitle(commonutilsR.string.commonutils_error)
                        setMessage(R.string.error_alias_already_exists)
                    }

                    GenerateURLError.URLExistsWithDifferentAlias -> {
                        setTitle(commonutilsR.string.commonutils_error)
                        setMessage(R.string.error_url_already_exists_with_different_alias)
                    }

                    GenerateURLError.InvalidURL -> {
                        setTitle(commonutilsR.string.commonutils_error)
                        setMessage(R.string.error_invalid_url)
                    }

                    GenerateURLError.InvalidAlias -> {
                        setTitle(commonutilsR.string.commonutils_error)
                        setMessage(R.string.error_invalid_alias)
                    }

                    GenerateURLError.InvalidURLOrAlias -> {
                        setTitle(commonutilsR.string.commonutils_error)
                        setMessage(R.string.error_invalid_url_or_alias)
                    }

                    GenerateURLError.InternalServerError -> {
                        setTitle(commonutilsR.string.commonutils_error)
                        setMessage(R.string.error_internal_server_error)
                    }

                    GenerateURLError.ServiceOffline -> {
                        setTitle(commonutilsR.string.commonutils_error)
                        setMessage(R.string.error_service_offline)
                    }

                    GenerateURLError.RateLimitExceeded -> {
                        setTitle(commonutilsR.string.commonutils_error)
                        setMessage(R.string.error_rate_limit_exceeded)
                    }

                    GenerateURLError.DomainNotAllowed -> {
                        setTitle(commonutilsR.string.commonutils_error)
                        setMessage(R.string.error_domain_not_allowed)
                    }
                }
            }
            .show()
    }
}
