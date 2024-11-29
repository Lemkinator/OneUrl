package de.lemke.oneurl.ui

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.SearchManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SearchView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.oneurl.R
import de.lemke.oneurl.data.UserSettings
import de.lemke.oneurl.databinding.ActivityMainBinding
import de.lemke.oneurl.domain.*
import de.lemke.oneurl.domain.model.URL
import dev.oneuiproject.oneui.delegates.AllSelectorState
import dev.oneuiproject.oneui.delegates.AppBarAwareYTranslator
import dev.oneuiproject.oneui.delegates.ViewYTranslator
import dev.oneuiproject.oneui.ktx.configureItemSwipeAnimator
import dev.oneuiproject.oneui.ktx.enableCoreSeslFeatures
import dev.oneuiproject.oneui.layout.ToolbarLayout
import dev.oneuiproject.oneui.layout.ToolbarLayout.SearchModeOnBackBehavior.DISMISS
import dev.oneuiproject.oneui.layout.startActionMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.util.Pair as UtilPair


@AndroidEntryPoint
class MainActivity : AppCompatActivity(), ViewYTranslator by AppBarAwareYTranslator() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var urlAdapter: URLAdapter
    private var allURLs: List<URL> = emptyList()
    private var urls: List<URL> = emptyList()
    private var searchURLs: List<URL> = emptyList()
    private var search: String? = null
    private val currentList get() = if (binding.drawerLayoutMain.isSearchMode) searchURLs else urls
    private var time: Long = 0
    private var initListJob: Job? = null
    private var isUIReady = false
    private var filterFavorite = false
    private val allSelectorStateFlow: MutableStateFlow<AllSelectorState> = MutableStateFlow(AllSelectorState())

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
                AppStart.NORMAL -> checkTOS(getUserSettings())
                AppStart.FIRST_TIME_VERSION -> checkTOS(getUserSettings())
            }
        }
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

    private suspend fun checkTOS(userSettings: UserSettings) {
        if (!userSettings.tosAccepted) openOOBE()
        else openMain()
    }

    private fun openMain() {
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
                    if (binding.drawerLayoutMain.isSearchMode) setSearchList()
                    else updateRecyclerView()
                    if (previousSize < it.size) {
                        binding.urlList.smoothScrollToPosition(0)
                    }
                }
            }
            binding.addFab.setOnClickListener {
                startActivity(
                    Intent(this@MainActivity, AddURLActivity::class.java),
                    ActivityOptions
                        .makeSceneTransitionAnimation(this@MainActivity, binding.addFab, "transition_fab")
                        .toBundle()
                )
            }
            //manually waiting for the animation to finish :/
            delay(700 - (System.currentTimeMillis() - time).coerceAtLeast(0L))
            isUIReady = true
        }
    }

    override fun onPause() {
        super.onPause()
        lifecycleScope.launch {
            delay(500) //delay, so closing the drawer is not visible for the user
            binding.drawerLayoutMain.setDrawerOpen(false, false)
        }
    }

    private fun checkIntent() {
        val extraText = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (intent?.action == Intent.ACTION_SEND && "text/plain" == intent.type && !extraText.isNullOrBlank()) {
            Log.d("MainActivity", "extraText: $extraText")
            startActivity(Intent(this@MainActivity, AddURLActivity::class.java).putExtra("url", extraText))
        }
        val textFromSelectMenu = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
        if (intent?.action == Intent.ACTION_PROCESS_TEXT && !textFromSelectMenu.isNullOrBlank()) {
            Log.d("MainActivity", "textFromSelectMenu: $textFromSelectMenu")
            startActivity(Intent(this@MainActivity, AddURLActivity::class.java).putExtra("url", textFromSelectMenu.toString()))
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent?.action == Intent.ACTION_SEARCH) binding.drawerLayoutMain.searchView.setQuery(
            intent.getStringExtra(SearchManager.QUERY),
            true
        )
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_item_search -> {
                binding.drawerLayoutMain.startSearchMode(SearchModeListener(), DISMISS)
                return true
            }

            R.id.menu_item_show_all -> {
                filterFavorite = false
                item.isVisible = false
                binding.root.toolbar.menu.findItem(R.id.menu_item_only_show_favorites).isVisible = true
                lifecycleScope.launch {
                    urls = allURLs
                    updateRecyclerView()
                }
                return true
            }

            R.id.menu_item_only_show_favorites -> {
                filterFavorite = true
                item.isVisible = false
                binding.root.toolbar.menu.findItem(R.id.menu_item_show_all).isVisible = true
                lifecycleScope.launch {
                    urls = allURLs.filter { it.favorite }
                    updateRecyclerView()
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
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
                    binding.addFab.hide()
                    searchView.setQuery(search, false)
                    val autoCompleteTextView = searchView.seslGetAutoCompleteView()
                    autoCompleteTextView.setText(search)
                    autoCompleteTextView.setSelection(autoCompleteTextView.text.length)
                    setSearchList()
                } else {
                    search = null
                    binding.addFab.show()
                    updateRecyclerView()
                    urlAdapter.highlightWord = ""
                }
            }
        }
    }

    @SuppressLint("RestrictedApi")
    private fun initDrawer() {
        val qrOption = findViewById<LinearLayout>(R.id.draweritem_generate_qr_code)
        val helpOption = findViewById<LinearLayout>(R.id.draweritem_help)
        val aboutAppOption = findViewById<LinearLayout>(R.id.draweritem_about_app)
        val aboutMeOption = findViewById<LinearLayout>(R.id.draweritem_about_me)
        val settingsOption = findViewById<LinearLayout>(R.id.draweritem_settings)

        qrOption.setOnClickListener {
            startActivity(Intent(this@MainActivity, GenerateQRCodeActivity::class.java))
        }
        helpOption.setOnClickListener { startActivity(Intent(this@MainActivity, HelpActivity::class.java)) }
        aboutAppOption.setOnClickListener { startActivity(Intent(this@MainActivity, AboutActivity::class.java)) }
        aboutMeOption.setOnClickListener { startActivity(Intent(this@MainActivity, AboutMeActivity::class.java)) }
        settingsOption.setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        binding.drawerLayoutMain.setDrawerButtonIcon(
            AppCompatResources.getDrawable(
                this@MainActivity,
                dev.oneuiproject.oneui.R.drawable.ic_oui_info_outline
            )
        )
        binding.drawerLayoutMain.setDrawerButtonOnClickListener {
            startActivity(Intent().setClass(this@MainActivity, AboutActivity::class.java))
        }
        binding.drawerLayoutMain.setDrawerButtonTooltip(getText(R.string.about_app))
        binding.drawerLayoutMain.searchView.setSearchableInfo(
            (getSystemService(SEARCH_SERVICE) as SearchManager).getSearchableInfo(componentName)
        )
        AppUpdateManagerFactory.create(this).appUpdateInfo.addOnSuccessListener { appUpdateInfo: AppUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE)
                binding.drawerLayoutMain.setButtonBadges(ToolbarLayout.Badge.Dot(), ToolbarLayout.Badge.Dot())
        }
        binding.urlNoEntryView.translateYWithAppBar(binding.drawerLayoutMain.appBarLayout, this)
        binding.drawerLayoutMain.findViewById<androidx.drawerlayout.widget.DrawerLayout>(dev.oneuiproject.oneui.design.R.id.drawerlayout_drawer)
            .addDrawerListener(
                object : androidx.drawerlayout.widget.DrawerLayout.DrawerListener {
                    override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
                    override fun onDrawerStateChanged(newState: Int) {}
                    override fun onDrawerOpened(drawerView: View) {
                        binding.addFab.hide()
                    }

                    override fun onDrawerClosed(drawerView: View) {
                        binding.addFab.show()
                    }
                }
            )
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
            addItemDecoration(ItemDecoration(context))
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
        if (!binding.drawerLayoutMain.isSearchMode && urls.isEmpty() || binding.drawerLayoutMain.isSearchMode && searchURLs.isEmpty()) {
            binding.urlList.visibility = View.GONE
            binding.urlListLottie.cancelAnimation()
            binding.urlListLottie.progress = 0f
            binding.urlNoEntryText.text = when {
                binding.drawerLayoutMain.isSearchMode -> getString(R.string.no_search_results)
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
                startActivity(
                    Intent(this@MainActivity, URLActivity::class.java)
                        .putExtra("shortURL", url.shortURL)
                        .putExtra("boldText", search),
                    ActivityOptions.makeSceneTransitionAnimation(
                        this@MainActivity,
                        UtilPair.create(viewHolder.listItemImg, "qr"),
                        //UtilPair.create(holder.listItemTitle, "shorturl"), buggy :/
                        //UtilPair.create(holder.listItemSubtitle1, "longurl"),
                        //UtilPair.create(holder.listItemSubtitle2, "added"),
                    ).toBundle()
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
                    Toast.makeText(this, getString(R.string.add_to_fav), Toast.LENGTH_SHORT).show()
                    lifecycleScope.launch {
                        updateURL(url.copy(favorite = true))
                    }
                }
                if (swipeDirection == END) {
                    Toast.makeText(this, getString(R.string.remove_from_fav), Toast.LENGTH_SHORT).show()
                    lifecycleScope.launch {
                        updateURL(url.copy(favorite = false))
                    }
                }
                true
            }
        )
    }

    private fun launchActionMode(initialSelected: Array<Long>? = null) {
        binding.drawerLayoutMain.startActionMode(
            onInflateMenu = { menu ->
                binding.addFab.hide()
                urlAdapter.onToggleActionMode(true, initialSelected)
                menuInflater.inflate(R.menu.menu_select, menu)
            },
            onEnd = {
                urlAdapter.onToggleActionMode(false)
                if (!binding.drawerLayoutMain.isSearchMode) binding.addFab.show()
            },
            onSelectMenuItem = {
                when (it.itemId) {
                    R.id.menu_item_delete -> {
                        lifecycleScope.launch {
                            deleteURL(currentList.filter { it.id in urlAdapter.getSelectedIds() })
                            binding.drawerLayoutMain.endActionMode()
                        }
                        true
                    }

                    R.id.menu_item_add_to_favorites -> {
                        lifecycleScope.launch {
                            updateURL(currentList.filter { it.id in urlAdapter.getSelectedIds() }.map { it.copy(favorite = true) })
                            binding.drawerLayoutMain.endActionMode()
                        }
                        true
                    }

                    R.id.menu_item_remove_from_favorites -> {
                        lifecycleScope.launch {
                            updateURL(currentList.filter { it.id in urlAdapter.getSelectedIds() }.map { it.copy(favorite = false) })
                            binding.drawerLayoutMain.endActionMode()
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