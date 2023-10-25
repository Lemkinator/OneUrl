package de.lemke.oneurl.ui

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.oneurl.R
import de.lemke.oneurl.databinding.ActivityAddUrlBinding
import de.lemke.oneurl.domain.AddURLUseCase
import de.lemke.oneurl.domain.GetURLUseCase
import de.lemke.oneurl.domain.GetUserSettingsUseCase
import de.lemke.oneurl.domain.UpdateUserSettingsUseCase
import de.lemke.oneurl.domain.generateURL.GenerateURLUseCase
import de.lemke.oneurl.domain.model.ShortURLProvider
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AddURLActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddUrlBinding
    private var addToFavorites = false

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    @Inject
    lateinit var generateURL: GenerateURLUseCase

    @Inject
    lateinit var addURL: AddURLUseCase

    @Inject
    lateinit var getURL: GetURLUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddUrlBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.setNavigationButtonOnClickListener {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(
                currentFocus?.windowToken,
                InputMethodManager.HIDE_NOT_ALWAYS
            )
            finishAfterTransition()
        }
        binding.root.tooltipText = getString(R.string.sesl_navigate_up)
        lifecycleScope.launch { initViews() }
        initFooterButton()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_fav, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_item_add_to_favorites -> {
                addToFavorites = true
                item.isVisible = false
                binding.root.toolbar.menu.findItem(R.id.menu_item_remove_from_favorites).isVisible = true
                return true
            }

            R.id.menu_item_remove_from_favorites -> {
                addToFavorites = false
                item.isVisible = false
                binding.root.toolbar.menu.findItem(R.id.menu_item_add_to_favorites).isVisible = true
                return true
            }

        }
        return super.onOptionsItemSelected(item)
    }

    private suspend fun initViews() {
        val adapter =
            ArrayAdapter(this@AddURLActivity, android.R.layout.simple_spinner_item, ShortURLProvider.values().map { it.toString() })
        adapter.setDropDownViewResource(androidx.appcompat.R.layout.support_simple_spinner_dropdown_item)
        binding.providerSpinner.adapter = adapter
        val userSettings = getUserSettings()
        binding.providerSpinner.setSelection(userSettings.selectedShortURLProvider.ordinal)
        onProviderChanged(userSettings.selectedShortURLProvider)
        binding.providerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                lifecycleScope.launch { onProviderChanged(ShortURLProvider.values()[p2]) }
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
        binding.editTextURL.setText(userSettings.lastURL)
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
    }

    private suspend fun onProviderChanged(provider: ShortURLProvider) {
        if (provider.aliasConfigurable) binding.textInputLayoutAlias.visibility = View.VISIBLE
        else binding.textInputLayoutAlias.visibility = View.GONE
        val userSettings = getUserSettings()
        if (provider == ShortURLProvider.OWOVCGAY && userSettings.showOWOVCGAYWarning) {
            AlertDialog.Builder(this@AddURLActivity)
                .setTitle(R.string.warning)
                .setMessage(R.string.owovcgay_warning)
                .setPositiveButton(R.string.ok, null)
                .setNegativeButton(R.string.dont_show_again) { _: DialogInterface, _: Int ->
                    lifecycleScope.launch { updateUserSettings { it.copy(showOWOVCGAYWarning = false) } }
                }
                .create()
                .show()
        } else if (provider == ShortURLProvider.OWOVCZWS && userSettings.showOWOVCZWSInfo) {
            AlertDialog.Builder(this@AddURLActivity)
                .setTitle(R.string.info)
                .setMessage(R.string.owovczws_info)
                .setPositiveButton(R.string.ok, null)
                .setNegativeButton(R.string.dont_show_again) { _: DialogInterface, _: Int ->
                    lifecycleScope.launch { updateUserSettings { it.copy(showOWOVCZWSInfo = false) } }
                }
                .create()
                .show()
        }
        updateUserSettings { it.copy(selectedShortURLProvider = provider) }
    }

    private fun initFooterButton() {
        if (resources.configuration.screenWidthDp < 360) {
            binding.addUrlFooterButton.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
        }
        binding.addUrlFooterButton.setOnClickListener { checkAndAddURL() }
    }

    private fun setLoading(loading: Boolean) {
        binding.addUrlFooterButtonProgress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.addUrlFooterButton.visibility = if (loading) View.GONE else View.VISIBLE
        binding.providerSpinner.isEnabled = !loading
        binding.editTextURL.isEnabled = !loading
        binding.editTextAlias.isEnabled = !loading
        binding.editTextDescription.isEnabled = !loading
        binding.root.toolbar.menu.findItem(R.id.menu_item_add_to_favorites).isEnabled = !loading
        binding.root.toolbar.menu.findItem(R.id.menu_item_remove_from_favorites).isEnabled = !loading
    }

    private fun checkAndAddURL() {
        val provider = ShortURLProvider.values()[binding.providerSpinner.selectedItemPosition]
        val alias = (binding.editTextAlias.text ?: "").toString().trim()
        if (binding.editTextURL.text.isNullOrBlank()) {
            binding.editTextURL.error = getString(R.string.error_empty_url)
            return
        }
        val longURL = provider.sanitizeURL(binding.editTextURL.text.toString())
        if (provider.aliasConfigurable && alias.isNotBlank()) {
            if (alias.length < provider.minAliasLength) {
                binding.editTextAlias.error = getString(R.string.error_alias_too_short)
                return
            }
            if (alias.length > provider.maxAliasLength) {
                binding.editTextAlias.error = getString(R.string.error_alias_too_long, provider.maxAliasLength)
                return
            }
            if (!provider.isAliasValid(alias)) {
                binding.editTextAlias.error = getString(R.string.error_invalid_alias, provider.allowedAliasCharacters)
                return
            }
        }
        setLoading(true)
        lifecycleScope.launch {
            val existingURL = getURL(provider, longURL)
            if (existingURL.isNotEmpty()) {
                if (alias.isBlank()) {
                    alreadyShortened(existingURL.first().shortURL)
                    return@launch
                }
                for (url in existingURL) {
                    if (url.shortURL == provider.baseURL + alias) {
                        alreadyShortened(url.shortURL)
                        return@launch
                    }
                }
            }
            generateURL(
                provider = provider,
                longURL = longURL,
                alias = alias,
                favorite = addToFavorites,
                description = binding.editTextDescription.text.toString(),
                errorCallback = {
                    lifecycleScope.launch {
                        AlertDialog.Builder(this@AddURLActivity)
                            .setTitle(R.string.error)
                            .setMessage(it)
                            .setPositiveButton(R.string.ok, null)
                            .create()
                            .show()
                        setLoading(false)
                    }
                },
                successCallback = {
                    lifecycleScope.launch {
                        addURL(it)
                        setLoading(false)
                        Toast.makeText(this@AddURLActivity, R.string.url_added, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                },
            )
        }
    }

    private fun alreadyShortened(shortURL: String) {
        AlertDialog.Builder(this@AddURLActivity)
            .setTitle(R.string.error)
            .setMessage(R.string.error_url_already_exists)
            .setNeutralButton(R.string.ok, null)
            .setPositiveButton(R.string.to_url) { _: DialogInterface, _: Int ->
                startActivity(Intent(this@AddURLActivity, URLActivity::class.java).putExtra("shortURL", shortURL))
                finish()
            }
            .create()
            .show()
        setLoading(false)
    }
}