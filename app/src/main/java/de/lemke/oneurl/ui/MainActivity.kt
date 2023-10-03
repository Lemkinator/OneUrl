package de.lemke.oneurl.ui

import android.util.Pair as UtilPair
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.util.SeslRoundedCorner
import androidx.appcompat.util.SeslSubheaderRoundedCorner
import androidx.appcompat.widget.SearchView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.SimpleColorFilter
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.color.MaterialColors
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.oneurl.R
import de.lemke.oneurl.data.UserSettings
import de.lemke.oneurl.databinding.ActivityMainBinding
import de.lemke.oneurl.domain.*
import de.lemke.oneurl.domain.model.Url
import de.lemke.oneurl.domain.utils.setCustomOnBackPressedLogic
import dev.oneuiproject.oneui.layout.DrawerLayout
import dev.oneuiproject.oneui.layout.ToolbarLayout
import dev.oneuiproject.oneui.utils.internal.ReflectUtils
import dev.oneuiproject.oneui.widget.Separator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlin.math.abs


@AndroidEntryPoint
class MainActivity : AppCompatActivity(R.layout.activity_main) {
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: UrlAdapter
    private var urls: List<Url> = emptyList()
    private var searchUrls: List<Url> = emptyList()
    private val backPressEnabled = MutableStateFlow(false)
    private var search: String? = null
    private val currentList get() = if (search == null) urls else searchUrls
    private var selected = HashMap<Int, Boolean>()
    private var selecting = false
    private var checkAllListening = true
    private var time: Long = 0
    private var initListJob: Job? = null
    private var isUIReady = false
    private var filterFavorite = false
    private val makeSectionOfTextBold: MakeSectionOfTextBoldUseCase = MakeSectionOfTextBoldUseCase()

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    @Inject
    lateinit var checkAppStart: CheckAppStartUseCase

    @Inject
    lateinit var getUrls: GetUrlsUseCase

    @Inject
    lateinit var observeUrls: ObserveUrlsUseCase

    @Inject
    lateinit var getSearchList: GetSearchListUseCase

    @Inject
    lateinit var deleteUrl: DeleteUrlUseCase

    @Inject
    lateinit var updateUrl: UpdateUrlUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        /*  Note: https://stackoverflow.com/a/69831106/18332741
        On Android 12 just running the app via android studio doesn't show the full splash screen.
        You have to kill it and open the app from the launcher.
        */
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
        finish()
    }

    private suspend fun checkTOS(userSettings: UserSettings) {
        if (!userSettings.tosAccepted) openOOBE()
        else openMain()
    }

    private fun openMain() {
        lifecycleScope.launch {
            setCustomOnBackPressedLogic(triggerStateFlow = backPressEnabled, onBackPressedLogic = { checkBackPressed() })
            initDrawer()
            urls = getUrls()
            initRecycler()
            binding.addFab.setOnClickListener {
                startActivity(
                    Intent(this@MainActivity, AddUrlActivity::class.java),
                    ActivityOptions
                        .makeSceneTransitionAnimation(this@MainActivity, binding.addFab, "transition_fab")
                        .toBundle()
                )
            }
            lifecycleScope.launch {
                observeUrls().flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collectLatest {
                    urls = if (filterFavorite) it.filter { url -> url.favorite } else it
                    updateRecyclerView()
                }
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

    private fun checkBackPressed() {
        when {
            selecting -> setSelecting(false)
            binding.drawerLayoutMain.isSearchMode -> {
                if (ViewCompat.getRootWindowInsets(binding.root)!!.isVisible(WindowInsetsCompat.Type.ime())) {
                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(
                        currentFocus!!.windowToken,
                        InputMethodManager.HIDE_NOT_ALWAYS
                    )
                } else {
                    search = null
                    binding.drawerLayoutMain.dismissSearchMode()
                }
            }

            binding.drawerLayoutMain.findViewById<androidx.drawerlayout.widget.DrawerLayout>(dev.oneuiproject.oneui.design.R.id.drawerlayout_drawer)
                .isDrawerOpen(
                    binding.drawerLayoutMain.findViewById<LinearLayout>(dev.oneuiproject.oneui.design.R.id.drawerlayout_drawer_content)
                ) -> {
                binding.drawerLayoutMain.setDrawerOpen(false, true)
            }

            else -> {
                //should not get here, callback should be disabled/unregistered
                finishAffinity()
            }
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
                binding.drawerLayoutMain.showSearchMode()
                return true
            }

            R.id.menu_item_show_all -> {
                filterFavorite = false
                item.isVisible = false
                binding.root.toolbar.menu.findItem(R.id.menu_item_only_show_favorites).isVisible = true
                lifecycleScope.launch {
                    urls = getUrls()
                    updateRecyclerView()
                }
                return true
            }

            R.id.menu_item_only_show_favorites -> {
                filterFavorite = true
                item.isVisible = false
                binding.root.toolbar.menu.findItem(R.id.menu_item_show_all).isVisible = true
                lifecycleScope.launch {
                    urls = urls.filter { url -> url.favorite }
                    updateRecyclerView()
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    inner class SearchModeListener : ToolbarLayout.SearchModeListener {
        override fun onQueryTextSubmit(query: String?): Boolean {
            if (search == null) return false
            lifecycleScope.launch {
                search = query ?: ""
                updateUserSettings { it.copy(search = query ?: "") }
                setSearchList()
            }
            return true
        }

        override fun onQueryTextChange(query: String?): Boolean {
            if (search == null) return false
            lifecycleScope.launch {
                search = query ?: ""
                updateUserSettings { it.copy(search = query ?: "") }
                setSearchList()
            }
            return true
        }

        override fun onSearchModeToggle(searchView: SearchView, visible: Boolean) {
            lifecycleScope.launch {
                if (visible) {
                    search = getUserSettings().search
                    binding.addFab.hide()
                    backPressEnabled.value = true
                    searchView.setQuery(search, false)
                    val autoCompleteTextView = searchView.seslGetAutoCompleteView()
                    autoCompleteTextView.setText(search)
                    autoCompleteTextView.setSelection(autoCompleteTextView.text.length)
                    setSearchList()
                } else {
                    search = null
                    binding.addFab.show()
                    backPressEnabled.value = false
                    updateRecyclerView()
                }
            }
        }
    }

    @SuppressLint("RestrictedApi")
    private fun initDrawer() {
        val helpOption = findViewById<LinearLayout>(R.id.draweritem_help)
        val aboutAppOption = findViewById<LinearLayout>(R.id.draweritem_about_app)
        val aboutMeOption = findViewById<LinearLayout>(R.id.draweritem_about_me)
        val settingsOption = findViewById<LinearLayout>(R.id.draweritem_settings)

        helpOption.setOnClickListener {
            startActivity(Intent(this@MainActivity, HelpActivity::class.java))
        }
        aboutAppOption.setOnClickListener {
            startActivity(Intent(this@MainActivity, AboutActivity::class.java))
        }
        aboutMeOption.setOnClickListener {
            startActivity(Intent(this@MainActivity, AboutMeActivity::class.java))
        }
        settingsOption.setOnClickListener {
            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
        }
        binding.drawerLayoutMain.setDrawerButtonIcon(getDrawable(dev.oneuiproject.oneui.R.drawable.ic_oui_info_outline))
        binding.drawerLayoutMain.setDrawerButtonOnClickListener {
            startActivity(Intent().setClass(this@MainActivity, AboutActivity::class.java))
        }
        binding.drawerLayoutMain.setDrawerButtonTooltip(getText(R.string.about_app))
        binding.drawerLayoutMain.setSearchModeListener(SearchModeListener())
        binding.drawerLayoutMain.searchView.setSearchableInfo(
            (getSystemService(SEARCH_SERVICE) as SearchManager).getSearchableInfo(componentName)
        )
        AppUpdateManagerFactory.create(this).appUpdateInfo.addOnSuccessListener { appUpdateInfo: AppUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE)
                binding.drawerLayoutMain.setButtonBadges(ToolbarLayout.N_BADGE, DrawerLayout.N_BADGE)
        }
        binding.drawerLayoutMain.appBarLayout.addOnOffsetChangedListener { layout: AppBarLayout, verticalOffset: Int ->
            val totalScrollRange = layout.totalScrollRange
            val inputMethodWindowVisibleHeight = ReflectUtils.genericInvokeMethod(
                InputMethodManager::class.java,
                getSystemService(INPUT_METHOD_SERVICE),
                "getInputMethodWindowVisibleHeight"
            ) as Int
            if (totalScrollRange != 0) binding.urlNoEntryView.translationY = (abs(verticalOffset) - totalScrollRange).toFloat() / 2.0f
            else binding.urlNoEntryView.translationY = (abs(verticalOffset) - inputMethodWindowVisibleHeight).toFloat() / 2.0f
        }
        binding.drawerLayoutMain.findViewById<androidx.drawerlayout.widget.DrawerLayout>(dev.oneuiproject.oneui.design.R.id.drawerlayout_drawer)
            .addDrawerListener(
                object : androidx.drawerlayout.widget.DrawerLayout.DrawerListener {
                    override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
                    override fun onDrawerOpened(drawerView: View) {
                        backPressEnabled.value = true
                        binding.addFab.hide()
                    }

                    override fun onDrawerClosed(drawerView: View) {
                        backPressEnabled.value = false
                        binding.addFab.show()
                    }

                    override fun onDrawerStateChanged(newState: Int) {}
                }
            )
    }

    fun setSearchList() {
        initListJob?.cancel()
        if (!this::binding.isInitialized) return
        initListJob = lifecycleScope.launch {
            searchUrls = getSearchList(search)
            updateRecyclerView()
        }
    }

    fun setSelecting(enabled: Boolean) {
        if (enabled) {
            selecting = true
            backPressEnabled.value = true
            binding.addFab.hide()
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
            binding.drawerLayoutMain.actionModeBottomMenu.clear()
            binding.drawerLayoutMain.setActionModeMenu(R.menu.menu_select)
            binding.drawerLayoutMain.setActionModeMenuListener { item: MenuItem ->
                lifecycleScope.launch {
                    when (item.itemId) {
                        R.id.menu_item_delete -> {
                            deleteUrl(currentList.filterIndexed { index, _ -> selected[index] ?: false })
                        }

                        R.id.menu_item_add_to_favorites -> {
                            updateUrl(currentList.filterIndexed { index, _ -> selected[index] ?: false }.map { it.copy(favorite = true) })
                        }

                        R.id.menu_item_remove_from_favorites -> {
                            updateUrl(currentList.filterIndexed { index, _ -> selected[index] ?: false }.map { it.copy(favorite = false) })
                        }
                    }
                }
                setSelecting(false)
                true
            }
            binding.drawerLayoutMain.showActionMode()
            binding.drawerLayoutMain.setActionModeCheckboxListener { _, isChecked ->
                if (checkAllListening) {
                    selected.replaceAll { _, _ -> isChecked }
                    adapter.notifyItemRangeChanged(0, adapter.itemCount)
                }
                val count = selected.values.count { it }
                binding.drawerLayoutMain.setActionModeAllSelector(count, true, count == urls.size)
            }
        } else {
            selecting = false
            for (i in 0 until adapter.itemCount) selected[i] = false
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
            binding.drawerLayoutMain.setActionModeAllSelector(0, true, false)
            binding.drawerLayoutMain.dismissActionMode()
            backPressEnabled.value = false
            binding.addFab.show()
        }
    }

    fun toggleItemSelected(position: Int) {
        selected[position] = !selected[position]!!
        adapter.notifyItemChanged(position)
        checkAllListening = false
        val count = selected.values.count { it }
        binding.drawerLayoutMain.setActionModeAllSelector(count, true, count == urls.size)
        checkAllListening = true
    }

    private fun initRecycler() {
        binding.urlList.layoutManager = LinearLayoutManager(this)
        adapter = UrlAdapter()
        binding.urlList.adapter = adapter
        binding.urlList.itemAnimator = null
        binding.urlList.addItemDecoration(ItemDecoration(this))
        binding.urlList.seslSetFastScrollerEnabled(true)
        binding.urlList.seslSetFillBottomEnabled(true)
        binding.urlList.seslSetGoToTopEnabled(true)
        binding.urlList.seslSetLastRoundedCorner(true)
        binding.urlList.seslSetSmoothScrollEnabled(true)
        binding.urlList.seslSetLongPressMultiSelectionListener(object : RecyclerView.SeslLongPressMultiSelectionListener {
            override fun onItemSelected(view: RecyclerView, child: View, position: Int, id: Long) {
                if (adapter.getItemViewType(position) == 0) toggleItemSelected(position)
            }

            override fun onLongPressMultiSelectionStarted(x: Int, y: Int) {}
            override fun onLongPressMultiSelectionEnded(x: Int, y: Int) {}
        })
        updateRecyclerView()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateRecyclerView() {
        if (search == null && urls.isEmpty() || search != null && searchUrls.isEmpty()) {
            binding.urlList.visibility = View.GONE
            binding.urlListLottie.cancelAnimation()
            binding.urlListLottie.progress = 0f
            binding.urlNoEntryText.text = when {
                search != null -> getString(R.string.no_search_results)
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
            selected = HashMap()
            currentList.indices.forEach { i -> selected[i] = false }
            adapter.notifyDataSetChanged()
        }
    }

    inner class UrlAdapter internal constructor() : RecyclerView.Adapter<UrlAdapter.ViewHolder>() {
        override fun getItemCount(): Int = currentList.size
        override fun getItemViewType(position: Int): Int = 0
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = if (viewType == 0) {
            val inflater = LayoutInflater.from(this@MainActivity)
            val view = inflater.inflate(R.layout.listview_item, parent, false)
            ViewHolder(view, false)
        } else ViewHolder(Separator(this@MainActivity), true)


        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val color = MaterialColors.getColor(
                this@MainActivity,
                androidx.appcompat.R.attr.colorPrimary,
                this@MainActivity.getColor(R.color.primary_color_themed)
            )
            holder.listItemTitle.text =
                if (search != null) makeSectionOfTextBold(currentList[position].shortUrl, search, color) else currentList[position].shortUrl
            holder.listItemSubtitle1.text =
                if (search != null) makeSectionOfTextBold(currentList[position].longUrl, search, color) else currentList[position].longUrl
            holder.listItemSubtitle2.text =
                if (search != null) makeSectionOfTextBold(currentList[position].addedFormatMedium, search, color)
                else currentList[position].added.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))
            if (selected[position]!!) holder.listItemImg.setImageResource(R.drawable.url_selected_icon)
            else holder.listItemImg.setImageBitmap(currentList[position].getResizedQr(150))
            holder.listItemFav.setImageResource(
                if (currentList[position].favorite) dev.oneuiproject.oneui.R.drawable.ic_oui_favorite_on
                else dev.oneuiproject.oneui.R.drawable.ic_oui_favorite_off
            )
            holder.parentView.setOnClickListener {
                if (selecting) toggleItemSelected(position)
                else {
                    startActivity(
                        Intent(this@MainActivity, UrlActivity::class.java)
                            .putExtra("shortUrl", currentList[position].shortUrl)
                            .putExtra("boldText", search),

                        ActivityOptions
                            .makeSceneTransitionAnimation(
                                this@MainActivity,
                                UtilPair.create(holder.listItemImg, "qr"),
                                //UtilPair.create(holder.listItemTitle, "shorturl"), buggy :/
                                //UtilPair.create(holder.listItemSubtitle1, "longurl"),
                                //UtilPair.create(holder.listItemSubtitle2, "added"),
                            )
                            .toBundle()
                    )
                }
            }
            holder.parentView.setOnLongClickListener {
                if (search != null) return@setOnLongClickListener false
                if (!selecting) setSelecting(true)
                toggleItemSelected(position)
                binding.urlList.seslStartLongPressMultiSelection()
                true
            }
        }

        inner class ViewHolder internal constructor(itemView: View, var isSeparator: Boolean) : RecyclerView.ViewHolder(itemView) {
            var parentView: LinearLayout
            var listItemImg: ImageView
            var listItemTitle: TextView
            var listItemSubtitle1: TextView
            var listItemSubtitle2: TextView
            var listItemFav: ImageView

            init {
                parentView = itemView as LinearLayout
                listItemImg = itemView.findViewById(R.id.list_item_img)
                listItemTitle = parentView.findViewById(R.id.list_item_title)
                listItemSubtitle1 = parentView.findViewById(R.id.list_item_subtitle1)
                listItemSubtitle2 = parentView.findViewById(R.id.list_item_subtitle2)
                listItemFav = parentView.findViewById(R.id.list_item_fav)
            }
        }
    }

    private inner class ItemDecoration(context: Context) : RecyclerView.ItemDecoration() {
        private val divider: Drawable?
        private val roundedCorner: SeslSubheaderRoundedCorner

        init {
            val outValue = TypedValue()
            context.theme.resolveAttribute(androidx.appcompat.R.attr.isLightTheme, outValue, true)
            divider = context.getDrawable(
                if (outValue.data == 0) androidx.appcompat.R.drawable.sesl_list_divider_dark
                else androidx.appcompat.R.drawable.sesl_list_divider_light
            )!!
            roundedCorner = SeslSubheaderRoundedCorner(this@MainActivity)
            roundedCorner.roundedCorners = SeslRoundedCorner.ROUNDED_CORNER_ALL
        }

        override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            super.onDraw(c, parent, state)
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                val holder = binding.urlList.getChildViewHolder(child) as UrlAdapter.ViewHolder
                if (!holder.isSeparator) {
                    val top = (child.bottom + (child.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin)
                    val bottom = divider!!.intrinsicHeight + top
                    divider.setBounds(parent.left, top, parent.right, bottom)
                    divider.draw(c)
                }
            }
        }

        override fun seslOnDispatchDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                val holder = binding.urlList.getChildViewHolder(child) as UrlAdapter.ViewHolder
                if (holder.isSeparator) roundedCorner.drawRoundedCorner(child, c)
            }
        }
    }
}