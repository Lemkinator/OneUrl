package de.lemke.oneurl.ui

import android.annotation.SuppressLint
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
import de.lemke.oneurl.domain.AddUrlUseCase
import de.lemke.oneurl.domain.GenerateUrlUseCase
import de.lemke.oneurl.domain.GetUserSettingsUseCase
import de.lemke.oneurl.domain.UpdateUserSettingsUseCase
import de.lemke.oneurl.domain.model.ShortUrlProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class AddUrlActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddUrlBinding
    private var addToFavorites = false

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    @Inject
    lateinit var generateUrl: GenerateUrlUseCase

    @Inject
    lateinit var addUrl: AddUrlUseCase

    @SuppressLint("SourceLockedOrientationActivity")
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
            ArrayAdapter(this@AddUrlActivity, android.R.layout.simple_spinner_item, ShortUrlProvider.values().map { it.toString() })
        adapter.setDropDownViewResource(androidx.appcompat.R.layout.support_simple_spinner_dropdown_item)
        binding.providerSpinner.adapter = adapter
        val userSettings = getUserSettings()
        binding.providerSpinner.setSelection(userSettings.selectedShortUrlProvider.ordinal)
        binding.providerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                lifecycleScope.launch {
                    updateUserSettings { it.copy(selectedShortUrlProvider = ShortUrlProvider.values()[p2]) }
                }
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
        binding.editTextUrl.setText(userSettings.lastUrl)
        binding.editTextUrl.requestFocus()
        binding.editTextUrl.text?.let { binding.editTextUrl.setSelection(0, it.length) }
        binding.editTextAlias.setText(userSettings.lastAlias)
        binding.editTextDescription.setText(userSettings.lastDescription)
        binding.editTextUrl.addTextChangedListener { text ->
            lifecycleScope.launch { updateUserSettings { it.copy(lastUrl = text.toString()) } }
        }
        binding.editTextAlias.addTextChangedListener { text ->
            lifecycleScope.launch { updateUserSettings { it.copy(lastAlias = text.toString()) } }
        }
        binding.editTextDescription.addTextChangedListener { text ->
            lifecycleScope.launch { updateUserSettings { it.copy(lastDescription = text.toString()) } }
        }
    }

    private fun initFooterButton() {
        if (resources.configuration.screenWidthDp < 360) {
            binding.oobeIntroFooterButton.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
        }
        binding.oobeIntroFooterButton.setOnClickListener {
            if (binding.editTextUrl.text.isNullOrBlank()) {
                binding.editTextUrl.error = getString(R.string.error_empty_url)
                return@setOnClickListener
            }
            setLoading(true)
            lifecycleScope.launch {
                delay(1000)
                generateUrl(
                    provider = ShortUrlProvider.values()[binding.providerSpinner.selectedItemPosition],
                    longUrl = binding.editTextUrl.text.toString(),
                    alias = binding.editTextAlias.text.toString(),
                    favorite = addToFavorites,
                    description = binding.editTextDescription.text.toString(),
                    errorCallback = {
                        lifecycleScope.launch {
                            setLoading(false)
                            AlertDialog.Builder(this@AddUrlActivity)
                                .setTitle(R.string.error)
                                .setMessage(it)
                                .setPositiveButton(R.string.ok, null)
                                .create()
                                .show()
                        }

                    },
                    successCallback = {
                        lifecycleScope.launch {
                            addUrl(it)
                            setLoading(false)
                            finish()
                            Toast.makeText(this@AddUrlActivity, R.string.url_added, Toast.LENGTH_SHORT).show()
                        }
                    },
                    alreadyShortenedCallback = {
                        lifecycleScope.launch {
                            setLoading(false)
                            AlertDialog.Builder(this@AddUrlActivity)
                                .setTitle(R.string.error)
                                .setMessage(R.string.url_already_exists)
                                .setNeutralButton(R.string.ok, null)
                                .setPositiveButton(R.string.to_url) { _: DialogInterface, _: Int ->
                                    finish()
                                    startActivity(
                                        Intent(this@AddUrlActivity, UrlActivity::class.java)
                                            .putExtra("shortUrl", it.shortUrl)
                                    )
                                }
                                .create()
                                .show()
                        }
                    }
                )
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.oobeIntroFooterButtonProgress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.oobeIntroFooterButton.visibility = if (loading) View.GONE else View.VISIBLE
        binding.providerSpinner.isEnabled = !loading
        binding.editTextUrl.isEnabled = !loading
        binding.editTextAlias.isEnabled = !loading
        binding.editTextDescription.isEnabled = !loading
        binding.root.toolbar.menu.findItem(R.id.menu_item_add_to_favorites).isEnabled = !loading
        binding.root.toolbar.menu.findItem(R.id.menu_item_remove_from_favorites).isEnabled = !loading
    }
}