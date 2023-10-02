package de.lemke.oneurl.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
        binding.root.setNavigationButtonOnClickListener { finish() }
        binding.root.tooltipText = getString(R.string.sesl_navigate_up)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, ShortUrlProvider.values().map { it.toString() })
        adapter.setDropDownViewResource(androidx.appcompat.R.layout.support_simple_spinner_dropdown_item)
        binding.providerSpinner.adapter = adapter
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
                binding.root.toolbar.menu.setGroupVisible(R.id.menu_group_add_to_favorites, false)
                binding.root.toolbar.menu.setGroupVisible(R.id.menu_group_remove_from_favorites, true)
                return true
            }
            R.id.menu_item_remove_from_favorites -> {
                addToFavorites = false
                binding.root.toolbar.menu.setGroupVisible(R.id.menu_group_add_to_favorites, true)
                binding.root.toolbar.menu.setGroupVisible(R.id.menu_group_remove_from_favorites, false)
                return true
            }

        }
        return super.onOptionsItemSelected(item)
    }

    private fun initFooterButton() {
        if (resources.configuration.screenWidthDp < 360) {
            binding.oobeIntroFooterButton.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
        }
        binding.oobeIntroFooterButton.setOnClickListener {
            setLoading(true)
            lifecycleScope.launch {
                delay(1000)
                generateUrl(
                    ShortUrlProvider.values()[binding.providerSpinner.selectedItemPosition],
                    binding.editTextUrl.text.toString(),
                    favorite = addToFavorites,
                    errorCallback = {
                        setLoading(false)
                        AlertDialog.Builder(this@AddUrlActivity)
                            .setTitle(R.string.error)
                            .setMessage(it)
                            .setPositiveButton(R.string.ok, null)
                            .create()
                            .show()

                    },
                    successCallback = {
                        lifecycleScope.launch {
                            addUrl(it)
                            setLoading(false)
                            Toast.makeText(this@AddUrlActivity, R.string.url_added, Toast.LENGTH_SHORT).show()
                            finish()
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
        binding.root.toolbar.menu.setGroupEnabled(R.id.menu_group_add_to_favorites, !loading)
        binding.root.toolbar.menu.setGroupEnabled(R.id.menu_group_remove_from_favorites, !loading)
    }
}