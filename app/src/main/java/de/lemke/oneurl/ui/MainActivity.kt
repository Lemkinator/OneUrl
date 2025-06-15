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
import android.graphics.ColorFilter
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
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper.END
import androidx.recyclerview.widget.ItemTouchHelper.START
import androidx.recyclerview.widget.LinearLayoutManager
import com.airbnb.lottie.LottieProperty.COLOR_FILTER
import com.airbnb.lottie.SimpleColorFilter
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.AboutActivity
import de.lemke.commonutils.AboutMeActivity
import de.lemke.commonutils.onNavigationSingleClick
import de.lemke.commonutils.prepareActivityTransformationFrom
import de.lemke.commonutils.restoreSearchAndActionMode
import de.lemke.commonutils.saveSearchAndActionMode
import de.lemke.commonutils.setupCommonActivities
import de.lemke.commonutils.setupHeaderAndNavRail
import de.lemke.commonutils.toast
import de.lemke.commonutils.transformToActivity
import de.lemke.oneurl.BuildConfig
import de.lemke.oneurl.R
import de.lemke.oneurl.data.UserSettings
import de.lemke.oneurl.databinding.ActivityMainBinding
import de.lemke.oneurl.domain.AppStart
import de.lemke.oneurl.domain.CheckAppStartUseCase
import de.lemke.oneurl.domain.DeleteURLUseCase
import de.lemke.oneurl.domain.GetUserSettingsUseCase
import de.lemke.oneurl.domain.ObserveURLsUseCase
import de.lemke.oneurl.domain.UpdateURLUseCase
import de.lemke.oneurl.domain.UpdateUserSettingsUseCase
import de.lemke.oneurl.domain.model.URL
import de.lemke.oneurl.ui.URLActivity.Companion.KEY_HIGHLIGHT_TEXT
import de.lemke.oneurl.ui.URLActivity.Companion.KEY_SHORTURL
import de.lemke.oneurl.ui.URLAdapter.Payload.SELECTION_MODE
import dev.oneuiproject.oneui.delegates.AllSelectorState
import dev.oneuiproject.oneui.delegates.AppBarAwareYTranslator
import dev.oneuiproject.oneui.delegates.ViewYTranslator
import dev.oneuiproject.oneui.ktx.configureImmBottomPadding
import dev.oneuiproject.oneui.ktx.configureItemSwipeAnimator
import dev.oneuiproject.oneui.ktx.dpToPx
import dev.oneuiproject.oneui.ktx.enableCoreSeslFeatures
import dev.oneuiproject.oneui.ktx.hideSoftInput
import dev.oneuiproject.oneui.ktx.hideSoftInputOnScroll
import dev.oneuiproject.oneui.ktx.onSingleClick
import dev.oneuiproject.oneui.layout.ToolbarLayout
import dev.oneuiproject.oneui.layout.ToolbarLayout.SearchModeOnBackBehavior.DISMISS
import dev.oneuiproject.oneui.layout.ToolbarLayout.SearchOnActionMode
import dev.oneuiproject.oneui.layout.startActionMode
import dev.oneuiproject.oneui.utils.ItemDecorRule.ALL
import dev.oneuiproject.oneui.utils.ItemDecorRule.NONE
import dev.oneuiproject.oneui.utils.SemItemDecoration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import dev.oneuiproject.oneui.R as iconsR
import dev.oneuiproject.oneui.design.R as designR


@AndroidEntryPoint
class MainActivity : AppCompatActivity(), ViewYTranslator by AppBarAwareYTranslator() {
    companion object {
        var scrollToTop = false
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var urlAdapter: URLAdapter
    private var urls: List<URL> = emptyList()
    private var time: Long = 0
    private var isUIReady = false
    private var filterFavorite: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private var search: MutableStateFlow<String?> = MutableStateFlow(null)
    private val allSelectorStateFlow: MutableStateFlow<AllSelectorState> = MutableStateFlow(AllSelectorState())

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    @Inject
    lateinit var checkAppStart: CheckAppStartUseCase

    @Inject
    lateinit var observeURLs: ObserveURLsUseCase

    @Inject
    lateinit var deleteURL: DeleteURLUseCase

    @Inject
    lateinit var updateURL: UpdateURLUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        prepareActivityTransformationFrom()
        time = System.currentTimeMillis()
        super.onCreate(savedInstanceState)
        if (SDK_INT >= 34) overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, fade_in, fade_out)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        splashScreen.setKeepOnScreenCondition { !isUIReady }
        /*
        there is a bug in the new splash screen api, when using the onExitAnimationListener -> splash icon flickers
        therefore setting a manual delay in openMain()
        splashScreen.setOnExitAnimationListener { splash ->
            val splashAnimator: ObjectAnimator = ObjectAnimator.ofPropertyValuesHolder(
                splash.view,
                PropertyValuesHolder.ofFloat(View.ALPHA, 0f),
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1.2f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.2f)
            )
            splashAnimator.interpolator = AccelerateDecelerateInterpolator()
            splashAnimator.duration = 400L
            splashAnimator.doOnEnd { splash.remove() }
            val contentAnimator: ObjectAnimator = ObjectAnimator.ofPropertyValuesHolder(
                binding.root,
                PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1.2f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.2f, 1f)
            )
            contentAnimator.interpolator = AccelerateDecelerateInterpolator()
            contentAnimator.duration = 400L

            val remainingDuration = splash.iconAnimationDurationMillis - (System.currentTimeMillis() - splash.iconAnimationStartMillis)
                .coerceAtLeast(0L)
            lifecycleScope.launch {
                delay(remainingDuration)
                splashAnimator.start()
                contentAnimator.start()
            }
        }*/

        lifecycleScope.launch {
            when (checkAppStart()) {
                AppStart.FIRST_TIME -> openOOBE()
                AppStart.NORMAL -> checkTOS(getUserSettings(), savedInstanceState)
                AppStart.FIRST_TIME_VERSION -> checkTOS(getUserSettings(), savedInstanceState)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!this::binding.isInitialized || !this::urlAdapter.isInitialized) return
        outState.saveSearchAndActionMode(
            isSearchMode = binding.drawerLayout.isSearchMode,
            isActionMode = binding.drawerLayout.isActionMode,
            selectedIds = urlAdapter.getSelectedIds()
        )
    }

    private suspend fun openOOBE() {
        //manually waiting for the animation to finish :/
        delay(700 - (System.currentTimeMillis() - time).coerceAtLeast(0L))
        startActivity(Intent(applicationContext, OOBEActivity::class.java))
        @Suppress("DEPRECATION") if (SDK_INT < 34) overridePendingTransition(fade_in, fade_out)
        finishAfterTransition()
    }

    private suspend fun checkTOS(userSettings: UserSettings, savedInstanceState: Bundle?) {
        if (!userSettings.tosAccepted) openOOBE()
        else openMain(savedInstanceState)
    }

    private fun openMain(savedInstanceState: Bundle?) {
        setupCommonUtilsActivities()
        initDrawer()
        initRecycler()
        checkIntent()
        savedInstanceState?.restoreSearchAndActionMode(onSearchMode = { startSearch() }, onActionMode = { launchActionMode(it) })
        binding.addFab.hideOnScroll(binding.urlList)
        binding.addFab.onSingleClick { binding.addFab.transformToActivity(Intent(this@MainActivity, AddURLActivity::class.java)) }
        lifecycleScope.launch {
            observeURLs(search, filterFavorite).flowWithLifecycle(lifecycle).collectLatest {
                urls = it
                updateRecyclerView()
                if (scrollToTop) {
                    delay(500)
                    binding.urlList.smoothScrollToPosition(0)
                    scrollToTop = false
                }
                //manually waiting for the splash animation to finish :/
                if (!isUIReady) delay(700 - (System.currentTimeMillis() - time).coerceAtLeast(0L))
                isUIReady = true
            }
        }
    }

    private fun checkIntent() {
        val extraText = intent.getStringExtra(EXTRA_TEXT)
        if (intent?.action == ACTION_SEND && "text/plain" == intent.type && !extraText.isNullOrBlank()) {
            Log.d("MainActivity", "extraText: $extraText")
            binding.addFab.transformToActivity(Intent(this, AddURLActivity::class.java).putExtra("url", extraText))
        }
        val textFromSelectMenu = intent.getCharSequenceExtra(EXTRA_PROCESS_TEXT)
        if (intent?.action == ACTION_PROCESS_TEXT && !textFromSelectMenu.isNullOrBlank()) {
            Log.d("MainActivity", "textFromSelectMenu: $textFromSelectMenu")
            binding.addFab.transformToActivity(Intent(this, AddURLActivity::class.java).putExtra("url", textFromSelectMenu.toString()))
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.action == ACTION_SEARCH) binding.drawerLayout.setSearchQueryFromIntent(intent)
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
            urlAdapter.highlightWord = search.value ?: ""
            lifecycleScope.launch { updateUserSettings { it.copy(search = query ?: "") } }
            return true
        }

        override fun onSearchModeToggle(searchView: SearchView, isActive: Boolean) {
            if (isActive) lifecycleScope.launch {
                search.value = getUserSettings().search
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

    private fun setupCommonUtilsActivities() {
        lifecycleScope.launch {
            setupCommonActivities(
                appName = getString(R.string.app_name),
                appVersion = BuildConfig.VERSION_NAME,
                optionalText = getString(R.string.app_description),
                email = getString(R.string.email),
                devModeEnabled = getUserSettings().devModeEnabled,
                onDevModeChanged = { newDevModeEnabled: Boolean -> updateUserSettings { it.copy(devModeEnabled = newDevModeEnabled) } }
            )
        }
    }

    @SuppressLint("RestrictedApi")
    private fun initDrawer() {
        binding.navigationView.onNavigationSingleClick { item ->
            when (item.itemId) {
                R.id.qr_code_dest -> findViewById<View>(R.id.qr_code_dest).transformToActivity(GenerateQRCodeActivity::class.java)
                R.id.provider_dest -> findViewById<View>(R.id.provider_dest).transformToActivity(ProviderActivity::class.java)
                R.id.help_dest -> findViewById<View>(R.id.help_dest).transformToActivity(HelpActivity::class.java)
                R.id.about_app_dest -> findViewById<View>(R.id.about_app_dest).transformToActivity(AboutActivity::class.java)
                R.id.about_me_dest -> findViewById<View>(R.id.about_me_dest).transformToActivity(AboutMeActivity::class.java)
                R.id.settings_dest -> findViewById<View>(R.id.settings_dest).transformToActivity(SettingsActivity::class.java)
                else -> return@onNavigationSingleClick false
            }
            true
        }
        binding.drawerLayout.setupHeaderAndNavRail(getString(R.string.about_app))
        //binding.drawerLayout.isImmersiveScroll = true
        binding.noEntryView.translateYWithAppBar(binding.drawerLayout.appBarLayout, this)
    }

    private fun initRecycler() {
        binding.urlList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = URLAdapter(context).also { it.setupOnClickListeners(); urlAdapter = it }
            itemAnimator = null
            addItemDecoration(SemItemDecoration(context, ALL, NONE).apply { setDividerInsetStart(92f.dpToPx(resources)) })
            enableCoreSeslFeatures()
            hideSoftInputOnScroll()
            if (SDK_INT >= VERSION_CODES.R) configureImmBottomPadding(binding.drawerLayout)
        }
        urlAdapter.configure(binding.urlList, SELECTION_MODE, onAllSelectorStateChanged = { allSelectorStateFlow.value = it })
        configureItemSwipeAnimator()
        updateRecyclerView()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateRecyclerView() {
        if (urls.isEmpty()) {
            binding.urlList.isVisible = false
            binding.noEntryLottie.cancelAnimation()
            binding.noEntryLottie.progress = 0f
            binding.noEntryText.text = when {
                search.value != null -> getString(R.string.no_search_results)
                filterFavorite.value -> getString(R.string.no_favorite_urls)
                else -> getString(R.string.no_urls)
            }
            binding.noEntryScrollView.isVisible = true
            val callback = LottieValueCallback<ColorFilter>(SimpleColorFilter(getColor(R.color.primary_color_themed)))
            binding.noEntryLottie.addValueCallback(KeyPath("**"), COLOR_FILTER, callback)
            binding.noEntryLottie.postDelayed({ binding.noEntryLottie.playAnimation() }, 400)
        } else {
            binding.noEntryScrollView.isVisible = false
            binding.urlList.isVisible = true
            urlAdapter.submitList(urls)
        }
    }

    private fun URLAdapter.setupOnClickListeners() {
        onClickItem = { position, url, viewHolder ->
            if (isActionMode) onToggleItem(url.id, position)
            else {
                hideSoftInput()
                viewHolder.itemView.transformToActivity(
                    Intent(this@MainActivity, URLActivity::class.java)
                        .putExtra(KEY_HIGHLIGHT_TEXT, search.value)
                        .putExtra(KEY_SHORTURL, url.shortURL)
                )
            }
        }
        onClickItemFavorite = { position, url -> lifecycleScope.launch { updateURL(url.copy(favorite = !url.favorite)) } }
        onLongClickItem = {
            if (!isActionMode) launchActionMode()
            binding.urlList.seslStartLongPressMultiSelection()
        }
    }

    private fun configureItemSwipeAnimator() {
        binding.urlList.configureItemSwipeAnimator(
            rightToLeftLabel = getString(R.string.add_to_fav),
            leftToRightLabel = getString(R.string.remove_from_fav),
            rightToLeftColor = getColor(R.color.primary_color_themed),
            leftToRightColor = getColor(designR.color.oui_des_functional_red_color),
            rightToLeftDrawableRes = iconsR.drawable.ic_oui_favorite_on,
            leftToRightDrawableRes = iconsR.drawable.ic_oui_delete_outline,
            isRightSwipeEnabled = { !urlAdapter.isActionMode },
            isLeftSwipeEnabled = { !urlAdapter.isActionMode },
            onSwiped = { position, swipeDirection, _ ->
                val url = urlAdapter.getItemByPosition(position)
                if (swipeDirection == START) {
                    toast(R.string.add_to_fav)
                    lifecycleScope.launch { updateURL(url.copy(favorite = true)) }
                }
                if (swipeDirection == END) {
                    toast(R.string.remove_from_fav)
                    lifecycleScope.launch { updateURL(url.copy(favorite = false)) }
                }
                true
            }
        )
    }

    private fun launchActionMode(initialSelected: Array<Long>? = null) {
        binding.addFab.isVisible = false
        urlAdapter.onToggleActionMode(true, initialSelected)
        binding.drawerLayout.startActionMode(
            onInflateMenu = { menu, menuInflater -> menuInflater.inflate(R.menu.menu_select, menu) },
            onEnd = {
                urlAdapter.onToggleActionMode(false)
                if (!binding.drawerLayout.isSearchMode) {
                    binding.addFab.isVisible = true
                    binding.addFab.show() //sometimes fab does not show after action mode ends
                }
            },
            onSelectMenuItem = {
                when (it.itemId) {
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