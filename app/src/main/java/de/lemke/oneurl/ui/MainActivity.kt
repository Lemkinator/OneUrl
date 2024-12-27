package de.lemke.oneurl.ui

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.MarginLayoutParams
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SearchView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper.END
import androidx.recyclerview.widget.ItemTouchHelper.START
import androidx.recyclerview.widget.LinearLayoutManager
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.SimpleColorFilter
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import com.skydoves.transformationlayout.TransformationCompat
import com.skydoves.transformationlayout.TransformationLayout
import com.skydoves.transformationlayout.onTransformationStartContainer
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.hideSoftInput
import de.lemke.commonutils.restoreSearchAndActionMode
import de.lemke.commonutils.saveSearchAndActionMode
import de.lemke.commonutils.toast
import de.lemke.oneurl.R
import de.lemke.oneurl.data.UserSettings
import de.lemke.oneurl.databinding.ActivityMainBinding
import de.lemke.oneurl.domain.*
import de.lemke.oneurl.domain.model.URL
import de.lemke.oneurl.ui.URLActivity.Companion.KEY_HIGHLIGHT_TEXT
import de.lemke.oneurl.ui.URLActivity.Companion.KEY_SHORTURL
import dev.oneuiproject.oneui.delegates.AllSelectorState
import dev.oneuiproject.oneui.delegates.AppBarAwareYTranslator
import dev.oneuiproject.oneui.delegates.ViewYTranslator
import dev.oneuiproject.oneui.ktx.configureItemSwipeAnimator
import dev.oneuiproject.oneui.ktx.dpToPx
import dev.oneuiproject.oneui.ktx.enableCoreSeslFeatures
import dev.oneuiproject.oneui.layout.Badge
import dev.oneuiproject.oneui.layout.DrawerLayout
import dev.oneuiproject.oneui.layout.ToolbarLayout
import dev.oneuiproject.oneui.layout.ToolbarLayout.SearchModeOnBackBehavior.DISMISS
import dev.oneuiproject.oneui.layout.startActionMode
import dev.oneuiproject.oneui.utils.ItemDecorRule
import dev.oneuiproject.oneui.utils.SemItemDecoration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : AppCompatActivity(), ViewYTranslator by AppBarAwareYTranslator() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var urlAdapter: URLAdapter
    private lateinit var drawerListView: LinearLayout
    private var allURLs: List<URL> = emptyList()
    private var urls: List<URL> = emptyList()
    private var searchURLs: List<URL> = emptyList()
    private var search: String? = null
    private val currentList get() = if (binding.drawerLayout.isSearchMode) searchURLs else urls
    private var time: Long = 0
    private var initListJob: Job? = null
    private var isUIReady = false
    private var filterFavorite = false
    private val allSelectorStateFlow: MutableStateFlow<AllSelectorState> = MutableStateFlow(AllSelectorState())
    private val drawerItemTitles: MutableList<TextView> = mutableListOf()

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    @Inject
    lateinit var checkAppStart: CheckAppStartUseCase

    @Inject
    lateinit var getURLs: GetURLsUseCase

    @Inject
    lateinit var observeURLs: ObserveURLsUseCase

    @Inject
    lateinit var getSearchList: GetSearchListUseCase

    @Inject
    lateinit var deleteURL: DeleteURLUseCase

    @Inject
    lateinit var updateURL: UpdateURLUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        onTransformationStartContainer()
        time = System.currentTimeMillis()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out)
        }
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
        outState.saveSearchAndActionMode(
            isSearchMode = binding.drawerLayout.isSearchMode,
            isActionMode = binding.drawerLayout.isActionMode,
            selectedIds = urlAdapter.getSelectedIds()
        )
        super.onSaveInstanceState(outState)
    }

    private suspend fun openOOBE() {
        //manually waiting for the animation to finish :/
        delay(700 - (System.currentTimeMillis() - time).coerceAtLeast(0L))
        startActivity(Intent(applicationContext, OOBEActivity::class.java))
        if (Build.VERSION.SDK_INT < 34) {
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        finishAfterTransition()
    }

    private suspend fun checkTOS(userSettings: UserSettings, savedInstanceState: Bundle?) {
        if (!userSettings.tosAccepted) openOOBE()
        else openMain(savedInstanceState)
    }

    private fun openMain(savedInstanceState: Bundle?) {
        lifecycleScope.launch {
            allURLs = getURLs()
            urls = if (filterFavorite) allURLs.filter { it.favorite } else allURLs
            initDrawer()
            initRecycler()
            checkIntent()
            lifecycleScope.launch {
                observeURLs().flowWithLifecycle(lifecycle).collectLatest {
                    val previousSize = allURLs.size
                    allURLs = it
                    urls = if (filterFavorite) it.filter { url -> url.favorite } else it
                    if (binding.drawerLayout.isSearchMode) setSearchList()
                    else updateRecyclerView()
                    if (previousSize < it.size) {
                        delay(500)
                        binding.urlList.smoothScrollToPosition(0)
                    }
                }
            }
            savedInstanceState?.restoreSearchAndActionMode(onSearchMode = { startSearch() }, onActionMode = { launchActionMode(it) })
            binding.addFab.hideOnScroll(binding.urlList)
            binding.addFab.setOnClickListener {
                TransformationCompat.startActivity(binding.fabTransformationLayout, Intent(this@MainActivity, AddURLActivity::class.java))
            }
            //manually waiting for the animation to finish :/
            delay(700 - (System.currentTimeMillis() - time).coerceAtLeast(0L))
            isUIReady = true
        }
    }

    private fun checkIntent() {
        val extraText = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (intent?.action == Intent.ACTION_SEND && "text/plain" == intent.type && !extraText.isNullOrBlank()) {
            Log.d("MainActivity", "extraText: $extraText")
            TransformationCompat.startActivity(
                binding.fabTransformationLayout,
                Intent(this@MainActivity, AddURLActivity::class.java).putExtra("url", extraText)
            )
        }
        val textFromSelectMenu = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
        if (intent?.action == Intent.ACTION_PROCESS_TEXT && !textFromSelectMenu.isNullOrBlank()) {
            Log.d("MainActivity", "textFromSelectMenu: $textFromSelectMenu")
            TransformationCompat.startActivity(
                binding.fabTransformationLayout,
                Intent(this@MainActivity, AddURLActivity::class.java).putExtra("url", textFromSelectMenu.toString())
            )
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent?.action == Intent.ACTION_SEARCH) binding.drawerLayout.searchView.setQuery(
            intent.getStringExtra(SearchManager.QUERY),
            true
        )
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.findItem(R.id.menu_item_show_all)?.isVisible = filterFavorite
        menu?.findItem(R.id.menu_item_only_show_favorites)?.isVisible = !filterFavorite
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_item_search -> {
                startSearch()
                return true
            }

            R.id.menu_item_show_all -> {
                filterFavorite = false
                invalidateOptionsMenu()
                lifecycleScope.launch {
                    urls = allURLs
                    updateRecyclerView()
                }
                return true
            }

            R.id.menu_item_only_show_favorites -> {
                filterFavorite = true
                invalidateOptionsMenu()
                lifecycleScope.launch {
                    urls = allURLs.filter { it.favorite }
                    updateRecyclerView()
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun startSearch() {
        binding.drawerLayout.startSearchMode(SearchModeListener(), DISMISS)
    }

    inner class SearchModeListener : ToolbarLayout.SearchModeListener {
        override fun onQueryTextSubmit(query: String?): Boolean = setSearch(query)
        override fun onQueryTextChange(query: String?): Boolean = setSearch(query)
        private fun setSearch(query: String?): Boolean {
            if (search == null) return false
            search = query ?: ""
            setSearchList()
            urlAdapter.highlightWord = search ?: ""
            lifecycleScope.launch {
                updateUserSettings { it.copy(search = query ?: "") }
            }
            return true
        }

        override fun onSearchModeToggle(searchView: SearchView, visible: Boolean) {
            lifecycleScope.launch {
                if (visible) {
                    search = getUserSettings().search
                    binding.addFab.isVisible = false
                    searchView.setQuery(search, false)
                    val autoCompleteTextView = searchView.seslGetAutoCompleteView()
                    autoCompleteTextView.setText(search)
                    autoCompleteTextView.setSelection(autoCompleteTextView.text.length)
                    setSearchList()
                } else {
                    search = null
                    binding.addFab.isVisible = !binding.drawerLayout.isActionMode
                    updateRecyclerView()
                    urlAdapter.highlightWord = ""
                }
            }
        }
    }

    fun closeDrawerAfterDelay() {
        if (binding.drawerLayout.isLargeScreenMode) return
        lifecycleScope.launch {
            delay(500) //delay, so closing the drawer is not visible for the user
            binding.drawerLayout.setDrawerOpen(false, false)
        }
    }

    @SuppressLint("RestrictedApi")
    private fun initDrawer() {
        drawerListView = findViewById(R.id.drawerListView)
        drawerItemTitles.apply {
            clear()
            add(findViewById(R.id.drawerItemQrTitle))
            add(findViewById(R.id.drawerItemHelpTitle))
            add(findViewById(R.id.drawerItemAboutAppTitle))
            add(findViewById(R.id.drawerItemAboutMeTitle))
            add(findViewById(R.id.drawerItemSettingsTitle))
        }
        findViewById<LinearLayout>(R.id.drawerItemQr).setOnClickListener {
            startActivity(Intent(this, GenerateQRCodeActivity::class.java))
            closeDrawerAfterDelay()
        }
        findViewById<LinearLayout>(R.id.drawerItemHelp).setOnClickListener {
            startActivity(Intent(this, HelpActivity::class.java))
            closeDrawerAfterDelay()
        }
        findViewById<LinearLayout>(R.id.drawerItemAboutApp).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
            closeDrawerAfterDelay()
        }
        findViewById<LinearLayout>(R.id.drawerItemAboutMe).setOnClickListener {
            startActivity(Intent(this, AboutMeActivity::class.java))
            closeDrawerAfterDelay()
        }
        findViewById<LinearLayout>(R.id.drawerItemSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            closeDrawerAfterDelay()
        }
        binding.drawerLayout.apply {
            setHeaderButtonIcon(AppCompatResources.getDrawable(this@MainActivity, dev.oneuiproject.oneui.R.drawable.ic_oui_info_outline))
            setHeaderButtonTooltip(getString(R.string.about_app))
            setHeaderButtonOnClickListener {
                startActivity(Intent(this@MainActivity, AboutActivity::class.java))
                closeDrawerAfterDelay()
            }
            searchView.setSearchableInfo((getSystemService(SEARCH_SERVICE) as SearchManager).getSearchableInfo(componentName))
            setNavRailContentMinSideMargin(14)
            lockNavRailOnActionMode = true
            lockNavRailOnSearchMode = true
            closeNavRailOnBack = true
        }

        AppUpdateManagerFactory.create(this).appUpdateInfo.addOnSuccessListener { appUpdateInfo: AppUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE)
                binding.drawerLayout.setButtonBadges(Badge.DOT, Badge.DOT)
        }
        binding.urlNoEntryView.translateYWithAppBar(binding.drawerLayout.appBarLayout, this)

        //setupNavRailFadeEffect
        binding.drawerLayout.apply {
            if (!isLargeScreenMode) return
            setDrawerStateListener {
                when (it) {
                    DrawerLayout.DrawerState.OPEN -> {
                        offsetUpdaterJob?.cancel()
                        updateOffset(1f)
                    }

                    DrawerLayout.DrawerState.CLOSE -> {
                        offsetUpdaterJob?.cancel()
                        updateOffset(0f)
                    }

                    DrawerLayout.DrawerState.CLOSING,
                    DrawerLayout.DrawerState.OPENING -> {
                        startOffsetUpdater()
                    }
                }
            }
        }

        //Set initial offset
        binding.drawerLayout.post {
            updateOffset(binding.drawerLayout.drawerOffset)
        }
    }

    private var offsetUpdaterJob: Job? = null
    private fun startOffsetUpdater() {
        //Ensure no duplicate job is running
        if (offsetUpdaterJob?.isActive == true) return
        offsetUpdaterJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                updateOffset(binding.drawerLayout.drawerOffset)
                delay(50)
            }
        }
    }

    fun updateOffset(offset: Float) {
        drawerItemTitles.forEach { it.alpha = offset }
        drawerListView.children.forEach {
            if (offset == 0f) {
                it.post {
                    it.updateLayoutParams<MarginLayoutParams> {
                        width = if (it is LinearLayout) 52f.dpToPx(it.context.resources) //drawer item
                        else 25f.dpToPx(it.context.resources) //divider item
                    }
                }
            } else {
                it.updateLayoutParams<MarginLayoutParams> {
                    width = MATCH_PARENT
                }
            }
        }
    }

    fun setSearchList() {
        initListJob?.cancel()
        if (!this::binding.isInitialized) return
        initListJob = lifecycleScope.launch {
            searchURLs = getSearchList(search, allURLs)
            updateRecyclerView()
        }
    }

    private fun initRecycler() {
        binding.urlList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = URLAdapter(context).also {
                it.setupOnClickListeners()
                urlAdapter = it
            }
            itemAnimator = null
            addItemDecoration(
                SemItemDecoration(context, dividerRule = ItemDecorRule.ALL, subHeaderRule = ItemDecorRule.NONE).apply {
                    setDividerInsetStart(92f.dpToPx(resources))
                }
            )
            enableCoreSeslFeatures()
        }

        urlAdapter.configure(
            binding.urlList,
            URLAdapter.Payload.SELECTION_MODE,
            onAllSelectorStateChanged = { allSelectorStateFlow.value = it }
        )
        configureItemSwipeAnimator()
        updateRecyclerView()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateRecyclerView() {
        if (!binding.drawerLayout.isSearchMode && urls.isEmpty() || binding.drawerLayout.isSearchMode && searchURLs.isEmpty()) {
            binding.urlList.visibility = View.GONE
            binding.urlListLottie.cancelAnimation()
            binding.urlListLottie.progress = 0f
            binding.urlNoEntryText.text = when {
                binding.drawerLayout.isSearchMode -> getString(R.string.no_search_results)
                filterFavorite -> getString(R.string.no_favorite_urls)
                else -> getString(R.string.no_urls)
            }
            binding.urlNoEntryScrollView.visibility = View.VISIBLE
            binding.urlListLottie.addValueCallback(
                KeyPath("**"),
                LottieProperty.COLOR_FILTER,
                LottieValueCallback(SimpleColorFilter(getColor(R.color.primary_color_themed)))
            )
            binding.urlListLottie.postDelayed({ binding.urlListLottie.playAnimation() }, 400)
        } else {
            binding.urlNoEntryScrollView.visibility = View.GONE
            binding.urlList.visibility = View.VISIBLE
            urlAdapter.submitList(currentList)
        }
    }

    private fun URLAdapter.setupOnClickListeners() {
        onClickItem = { position, url, viewHolder ->
            if (isActionMode) onToggleItem(url.id, position)
            else {
                val transformationLayout = viewHolder.itemView as TransformationLayout
                hideSoftInput()
                TransformationCompat.startActivity(
                    transformationLayout,
                    Intent(this@MainActivity, URLActivity::class.java)
                        //.putExtra("com.skydoves.transformationlayout", transformationLayout.getParcelableParams())
                        .putExtra(KEY_HIGHLIGHT_TEXT, search)
                        .putExtra(KEY_SHORTURL, url.shortURL)
                )
            }
        }
        onClickItemFavorite = { position, url ->
            lifecycleScope.launch { updateURL(url.copy(favorite = !url.favorite)) }
        }
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
            leftToRightColor = getColor(dev.oneuiproject.oneui.design.R.color.oui_functional_red_color),
            rightToLeftDrawableRes = dev.oneuiproject.oneui.R.drawable.ic_oui_favorite_on,
            leftToRightDrawableRes = dev.oneuiproject.oneui.R.drawable.ic_oui_delete_outline,
            isRightSwipeEnabled = { !urlAdapter.isActionMode },
            isLeftSwipeEnabled = { !urlAdapter.isActionMode },
            onSwiped = { position, swipeDirection, _ ->
                val url = urlAdapter.getItemByPosition(position)
                if (swipeDirection == START) {
                    toast(R.string.add_to_fav)
                    lifecycleScope.launch {
                        updateURL(url.copy(favorite = true))
                    }
                }
                if (swipeDirection == END) {
                    toast(R.string.remove_from_fav)
                    lifecycleScope.launch {
                        updateURL(url.copy(favorite = false))
                    }
                }
                true
            }
        )
    }

    private fun launchActionMode(initialSelected: Array<Long>? = null) {
        binding.drawerLayout.startActionMode(
            onInflateMenu = { menu ->
                binding.addFab.isVisible = false
                urlAdapter.onToggleActionMode(true, initialSelected)
                menuInflater.inflate(R.menu.menu_select, menu)
            },
            onEnd = {
                urlAdapter.onToggleActionMode(false)
                binding.addFab.isVisible = !binding.drawerLayout.isSearchMode
            },
            onSelectMenuItem = {
                when (it.itemId) {
                    R.id.menu_item_delete -> {
                        lifecycleScope.launch {
                            deleteURL(currentList.filter { it.id in urlAdapter.getSelectedIds() })
                            binding.drawerLayout.endActionMode()
                        }
                        true
                    }

                    R.id.menu_item_add_to_favorites -> {
                        lifecycleScope.launch {
                            updateURL(currentList.filter { it.id in urlAdapter.getSelectedIds() }.map { it.copy(favorite = true) })
                            binding.drawerLayout.endActionMode()
                        }
                        true
                    }

                    R.id.menu_item_remove_from_favorites -> {
                        lifecycleScope.launch {
                            updateURL(currentList.filter { it.id in urlAdapter.getSelectedIds() }.map { it.copy(favorite = false) })
                            binding.drawerLayout.endActionMode()
                        }
                        true
                    }

                    else -> false
                }
            },
            onSelectAll = { isChecked: Boolean -> urlAdapter.onToggleSelectAll(isChecked) },
            allSelectorStateFlow = allSelectorStateFlow,
            keepSearchMode = true
        )
    }
}