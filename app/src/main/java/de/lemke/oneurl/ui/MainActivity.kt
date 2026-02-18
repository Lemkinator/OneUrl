package de.lemke.oneurl.ui

import android.R.anim.fade_in
import android.R.anim.fade_out
import android.annotation.SuppressLint
import android.content.Intent
import android.content.Intent.ACTION_PROCESS_TEXT
import android.content.Intent.ACTION_SEARCH
import android.content.Intent.ACTION_SEND
import android.content.Intent.EXTRA_PROCESS_TEXT
import android.content.Intent.EXTRA_TEXT
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.preference.SwitchPreferenceCompat
import androidx.recyclerview.widget.ItemTouchHelper.END
import androidx.recyclerview.widget.ItemTouchHelper.START
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.checkAppStartAndHandleOOBE
import de.lemke.commonutils.configureCommonUtilsSplashScreen
import de.lemke.commonutils.data.commonUtilsSettings
import de.lemke.commonutils.onNavigationSingleClick
import de.lemke.commonutils.prepareActivityTransformationFrom
import de.lemke.commonutils.restoreSearchAndActionMode
import de.lemke.commonutils.saveSearchAndActionMode
import de.lemke.commonutils.setupCommonUtilsAboutActivity
import de.lemke.commonutils.setupCommonUtilsOOBEActivity
import de.lemke.commonutils.setupCommonUtilsSettingsActivity
import de.lemke.commonutils.setupHeaderAndNavRail
import de.lemke.commonutils.toast
import de.lemke.commonutils.transformToActivity
import de.lemke.commonutils.ui.activity.CommonUtilsAboutActivity
import de.lemke.commonutils.ui.activity.CommonUtilsAboutMeActivity
import de.lemke.commonutils.ui.activity.CommonUtilsSettingsActivity
import de.lemke.oneurl.BuildConfig
import de.lemke.oneurl.R
import de.lemke.oneurl.databinding.ActivityMainBinding
import de.lemke.oneurl.domain.DeleteURLUseCase
import de.lemke.oneurl.domain.GetUserSettingsUseCase
import de.lemke.oneurl.domain.ObserveURLsUseCase
import de.lemke.oneurl.domain.UpdateURLUseCase
import de.lemke.oneurl.domain.UpdateUserSettingsUseCase
import de.lemke.oneurl.domain.model.URL
import de.lemke.oneurl.ui.URLActivity.Companion.KEY_HIGHLIGHT_TEXT
import de.lemke.oneurl.ui.URLActivity.Companion.KEY_SHORTURL
import dev.oneuiproject.oneui.delegates.AppBarAwareYTranslator
import dev.oneuiproject.oneui.delegates.ViewYTranslator
import dev.oneuiproject.oneui.ktx.dpToPx
import dev.oneuiproject.oneui.ktx.hideSoftInput
import dev.oneuiproject.oneui.ktx.onNewValue
import dev.oneuiproject.oneui.ktx.onSingleClick
import dev.oneuiproject.oneui.layout.ToolbarLayout
import dev.oneuiproject.oneui.layout.ToolbarLayout.AllSelectorState
import dev.oneuiproject.oneui.layout.ToolbarLayout.SearchModeOnBackBehavior.DISMISS
import dev.oneuiproject.oneui.layout.ToolbarLayout.SearchOnActionMode
import dev.oneuiproject.oneui.layout.startActionMode
import dev.oneuiproject.oneui.recyclerview.ktx.configureImmBottomPadding
import dev.oneuiproject.oneui.recyclerview.ktx.configureItemSwipeAnimator
import dev.oneuiproject.oneui.recyclerview.ktx.enableCoreSeslFeatures
import dev.oneuiproject.oneui.recyclerview.ktx.hideSoftInputOnScroll
import dev.oneuiproject.oneui.utils.ItemDecorRule.ALL
import dev.oneuiproject.oneui.utils.ItemDecorRule.NONE
import dev.oneuiproject.oneui.utils.SemItemDecoration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import de.lemke.commonutils.R as commonutilsR
import dev.oneuiproject.oneui.R as iconsR
import dev.oneuiproject.oneui.design.R as designR


@AndroidEntryPoint
class MainActivity : AppCompatActivity(), ViewYTranslator by AppBarAwareYTranslator() {
    private lateinit var binding: ActivityMainBinding
    private var urls: List<URL> = emptyList()
    private var isUIReady = false
    private var filterFavorite: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private var search: MutableStateFlow<String?> = MutableStateFlow(null)
    private val allSelectorStateFlow: MutableStateFlow<AllSelectorState> = MutableStateFlow(AllSelectorState())
    private val urlAdapter: URLAdapter by lazy {
        URLAdapter(this, onAllSelectorStateChanged = { allSelectorStateFlow.value = it }, onBlockActionMode = ::launchActionMode)
    }

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    @Inject
    lateinit var observeURLs: ObserveURLsUseCase

    @Inject
    lateinit var deleteURL: DeleteURLUseCase

    @Inject
    lateinit var updateURL: UpdateURLUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        prepareActivityTransformationFrom()
        super.onCreate(savedInstanceState)
        if (SDK_INT >= 34) overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, fade_in, fade_out)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureCommonUtilsSplashScreen(splashScreen, binding.root) { !isUIReady }
        setupCommonUtilsOOBEActivity(nextActivity = MainActivity::class.java)
        if (!checkAppStartAndHandleOOBE(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)) openMain(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!this::binding.isInitialized) return
        outState.saveSearchAndActionMode(
            isSearchMode = binding.drawerLayout.isSearchMode,
            isActionMode = binding.drawerLayout.isActionMode,
            selectedIds = urlAdapter.getSelectedIds()
        )
    }

    private fun openMain(savedInstanceState: Bundle?) {
        setupCommonUtilsAboutActivity(appVersion = BuildConfig.VERSION_NAME)
        setupCommonUtilsSettingsActivity(
            commonutilsR.xml.preferences_design,
            R.xml.preferences,
            commonutilsR.xml.preferences_dev_options_delete_app_data,
            commonutilsR.xml.preferences_more_info
        ) {
            findPreference<SwitchPreferenceCompat>("auto_copy_on_create_pref")?.let { autoCopyOnCreatePref ->
                autoCopyOnCreatePref.isChecked = getUserSettings().autoCopyOnCreate
                autoCopyOnCreatePref.onNewValue { newValue: Boolean ->
                    lifecycleScope.launch { updateUserSettings { it.copy(autoCopyOnCreate = newValue) } }
                }
            }
        }
        initDrawer()
        initRecycler()
        savedInstanceState?.restoreSearchAndActionMode(onSearchMode = { startSearch() }, onActionMode = { launchActionMode(it) })
        binding.addFab.hideOnScroll(binding.urlList)
        binding.addFab.onSingleClick { binding.addFab.transformToActivity(AddURLActivity::class.java, "AddURLTransition") }
        lifecycleScope.launch {
            observeURLs(search, filterFavorite).flowWithLifecycle(lifecycle, RESUMED).collectLatest {
                val previousSize = urls.size
                urls = it
                updateRecyclerView()
                if (it.size > previousSize) binding.urlList.smoothScrollToPosition(0)
                if (!isUIReady) {
                    checkIntent()
                    isUIReady = true
                }
            }
        }
    }

    private fun checkIntent() {
        val extraText = intent.getStringExtra(EXTRA_TEXT)
        if (intent?.action == ACTION_SEND && "text/plain" == intent.type && !extraText.isNullOrBlank()) {
            Log.d("MainActivity", "extraText: $extraText")
            binding.addFab.transformToActivity(Intent(this, AddURLActivity::class.java).putExtra("url", extraText), "AddURLTransition")
        }
        val textFromSelectMenu = intent.getCharSequenceExtra(EXTRA_PROCESS_TEXT)
        if (intent?.action == ACTION_PROCESS_TEXT && !textFromSelectMenu.isNullOrBlank()) {
            Log.d("MainActivity", "textFromSelectMenu: $textFromSelectMenu")
            binding.addFab.transformToActivity(
                Intent(this, AddURLActivity::class.java).putExtra("url", textFromSelectMenu.toString()),
                "AddURLTransition"
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_SEARCH) binding.drawerLayout.setSearchQueryFromIntent(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean = menuInflater.inflate(R.menu.main, menu).let { true }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.findItem(R.id.menu_item_show_all)?.isVisible = filterFavorite.value
        menu?.findItem(R.id.menu_item_only_show_favorites)?.isVisible = !filterFavorite.value
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.menu_item_search -> startSearch().let { true }
        R.id.menu_item_show_all -> {
            filterFavorite.value = false
            invalidateOptionsMenu()
            true
        }

        R.id.menu_item_only_show_favorites -> {
            filterFavorite.value = true
            invalidateOptionsMenu()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private fun startSearch() = binding.drawerLayout.startSearchMode(searchModeListener, DISMISS)

    val searchModeListener = object : ToolbarLayout.SearchModeListener {
        override fun onQueryTextSubmit(query: String?): Boolean = setSearch(query).also { hideSoftInput() }
        override fun onQueryTextChange(query: String?): Boolean = setSearch(query)
        private fun setSearch(query: String?): Boolean {
            if (search.value == null) return false
            search.value = query ?: ""
            urlAdapter.highlightWord = query ?: ""
            commonUtilsSettings.search = query ?: ""
            return true
        }

        override fun onSearchModeToggle(searchView: SearchView, isActive: Boolean) {
            if (isActive) {
                search.value = commonUtilsSettings.search
                binding.addFab.isVisible = false
                searchView.setQuery(search.value, false)
            } else {
                search.value = null
                if (!binding.drawerLayout.isActionMode) {
                    binding.addFab.isVisible = true
                    binding.addFab.show() //sometimes fab does not show after action mode ends
                }
                urlAdapter.highlightWord = ""
            }
        }
    }

    @SuppressLint("RestrictedApi")
    private fun initDrawer() {
        binding.navigationView.onNavigationSingleClick { item ->
            when (item.itemId) {
                R.id.qr_code_dest -> findViewById<View>(R.id.qr_code_dest).transformToActivity(GenerateQRCodeActivity::class.java)
                R.id.provider_dest -> findViewById<View>(R.id.provider_dest).transformToActivity(ProviderActivity::class.java)
                R.id.help_dest -> findViewById<View>(R.id.help_dest).transformToActivity(HelpActivity::class.java)
                R.id.about_app_dest -> findViewById<View>(R.id.about_app_dest).transformToActivity(CommonUtilsAboutActivity::class.java)
                R.id.about_me_dest -> findViewById<View>(R.id.about_me_dest).transformToActivity(CommonUtilsAboutMeActivity::class.java)
                R.id.settings_dest -> findViewById<View>(R.id.settings_dest).transformToActivity(CommonUtilsSettingsActivity::class.java)
                else -> return@onNavigationSingleClick false
            }
            true
        }
        binding.drawerLayout.setTitle(BuildConfig.APP_NAME)
        binding.drawerLayout.setupHeaderAndNavRail(getString(R.string.about_app))
        //binding.drawerLayout.isImmersiveScroll = true
        binding.noEntryView.translateYWithAppBar(binding.drawerLayout.appBarLayout, this)
    }

    private fun initRecycler() {
        binding.urlList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = urlAdapter.also { it.setupOnClickListeners() }
            itemAnimator = null
            addItemDecoration(SemItemDecoration(context, ALL, NONE).apply { setDividerInsetStart(92f.dpToPx(resources)) })
            enableCoreSeslFeatures()
            hideSoftInputOnScroll()
            if (SDK_INT >= VERSION_CODES.R) configureImmBottomPadding(binding.drawerLayout)
            urlAdapter.configureWith(this)
        }
        configureItemSwipeAnimator()
        updateRecyclerView()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateRecyclerView() {
        if (urls.isNotEmpty()) urlAdapter.submitList(urls)
        else binding.noEntryView.text = when {
            search.value != null -> getString(commonutilsR.string.commonutils_no_results_found)
            filterFavorite.value -> getString(R.string.no_favorite_urls)
            else -> getString(R.string.no_urls)
        }
        binding.noEntryView.updateVisibilityWith(urls, binding.urlList)
    }

    private fun URLAdapter.setupOnClickListeners() {
        onClickItem = { position, url, viewHolder ->
            if (isActionMode) toggleItem(url.id, position)
            else {
                hideSoftInput()
                viewHolder.itemView.transformToActivity(
                    Intent(this@MainActivity, URLActivity::class.java)
                        .putExtra(KEY_HIGHLIGHT_TEXT, search.value)
                        .putExtra(KEY_SHORTURL, url.shortURL)
                )
            }
        }
        onClickItemFavorite = { _, url -> lifecycleScope.launch { updateURL(url.copy(favorite = !url.favorite)) } }
        onLongClickItem = {
            if (!isActionMode) launchActionMode()
            binding.urlList.seslStartLongPressMultiSelection()
        }
    }

    private fun configureItemSwipeAnimator() {
        binding.urlList.configureItemSwipeAnimator(
            rightToLeftLabel = getString(commonutilsR.string.commonutils_add_to_fav),
            leftToRightLabel = getString(commonutilsR.string.commonutils_remove_from_fav),
            rightToLeftColor = getColor(R.color.primary_color_themed),
            leftToRightColor = getColor(designR.color.oui_des_functional_red_color),
            rightToLeftDrawableRes = iconsR.drawable.ic_oui_favorite_on,
            leftToRightDrawableRes = iconsR.drawable.ic_oui_delete_outline,
            isRightSwipeEnabled = { !urlAdapter.isActionMode },
            isLeftSwipeEnabled = { !urlAdapter.isActionMode },
            onSwiped = { position, swipeDirection, _ ->
                val url = urlAdapter.getItemByPosition(position)
                if (swipeDirection == START) {
                    toast(commonutilsR.string.commonutils_add_to_fav)
                    lifecycleScope.launch { updateURL(url.copy(favorite = true)) }
                }
                if (swipeDirection == END) {
                    toast(commonutilsR.string.commonutils_remove_from_fav)
                    lifecycleScope.launch { updateURL(url.copy(favorite = false)) }
                }
                true
            }
        )
    }

    private fun launchActionMode(initialSelected: Set<Long>? = null) {
        binding.addFab.isVisible = false
        urlAdapter.toggleActionMode(true, initialSelected)
        binding.drawerLayout.startActionMode(
            onInflateMenu = { menu, menuInflater -> menuInflater.inflate(R.menu.menu_select, menu) },
            onEnd = {
                urlAdapter.toggleActionMode(false)
                if (!binding.drawerLayout.isSearchMode) {
                    binding.addFab.isVisible = true
                    binding.addFab.show() //sometimes fab does not show after action mode ends
                }
            },
            onSelectMenuItem = { menuItem ->
                when (menuItem.itemId) {
                    R.id.menu_item_delete -> lifecycleScope.launch {
                        deleteURL(urls.filter { it.id in urlAdapter.getSelectedIds() })
                        binding.drawerLayout.endActionMode()
                    }.let { true }

                    R.id.menu_item_add_to_favorites -> lifecycleScope.launch {
                        updateURL(urls.filter { it.id in urlAdapter.getSelectedIds() }.map { it.copy(favorite = true) })
                        binding.drawerLayout.endActionMode()
                    }.let { true }

                    R.id.menu_item_remove_from_favorites -> lifecycleScope.launch {
                        updateURL(urls.filter { it.id in urlAdapter.getSelectedIds() }.map { it.copy(favorite = false) })
                        binding.drawerLayout.endActionMode()
                    }.let { true }

                    else -> false
                }
            },
            onSelectAll = { isChecked: Boolean -> urlAdapter.onToggleSelectAll(isChecked) },
            allSelectorStateFlow = allSelectorStateFlow,
            searchOnActionMode = SearchOnActionMode.NoDismiss
        )
    }
}