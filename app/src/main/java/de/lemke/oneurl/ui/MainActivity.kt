package de.lemke.oneurl.ui

import android.annotation.SuppressLint
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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.util.SeslRoundedCorner
import androidx.appcompat.util.SeslSubheaderRoundedCorner
import androidx.appcompat.widget.SearchView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.SimpleColorFilter
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback
import com.google.android.material.appbar.AppBarLayout
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.oneurl.R
import de.lemke.oneurl.data.UserSettings
import de.lemke.oneurl.databinding.ActivityMainBinding
import de.lemke.oneurl.domain.*
import de.lemke.oneurl.domain.model.Url
import de.lemke.oneurl.ui.dialog.AddUrlDialog
import dev.oneuiproject.oneui.layout.DrawerLayout
import dev.oneuiproject.oneui.layout.ToolbarLayout
import dev.oneuiproject.oneui.utils.internal.ReflectUtils
import dev.oneuiproject.oneui.widget.Separator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class MainActivity : AppCompatActivity(R.layout.activity_main) {
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: UrlAdapter
    private lateinit var urls: List<Url>
    private val backPressEnabled = MutableStateFlow(false)
    private var selected = HashMap<Int, Boolean>()
    private var selecting = false
    private var checkAllListening = true
    private var isSearchUserInputEnabled = false
    private var time: Long = 0
    private var initListJob: Job? = null
    private var isUIReady = false

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    @Inject
    lateinit var checkAppStart: CheckAppStartUseCase

    @Inject
    lateinit var getUrls: GetUrlsUseCase

    @Inject
    lateinit var getSearchList: GetSearchListUseCase

    @Inject
    lateinit var addUrl: AddUrlUseCase

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
            initList()
            binding.addFab.setOnClickListener {
                val dialog = AddUrlDialog()
                dialog.show(supportFragmentManager, "addUrlDialog")

                /*DialogUtils.setDialogProgressForButton(dialog, DialogInterface.BUTTON_POSITIVE) {
                    lifecycleScope.launch {
                        delay(500)
                        dialog.dismiss()
                    }
                }*/
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
            binding.drawerLayoutMain.isSearchMode -> {
                if (ViewCompat.getRootWindowInsets(binding.root)!!.isVisible(WindowInsetsCompat.Type.ime())) {
                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(
                        currentFocus!!.windowToken,
                        InputMethodManager.HIDE_NOT_ALWAYS
                    )
                } else {
                    isSearchUserInputEnabled = false
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

        }
        return super.onOptionsItemSelected(item)
    }

    inner class SearchModeListener : ToolbarLayout.SearchModeListener {
        override fun onQueryTextSubmit(query: String?): Boolean {
            if (!isSearchUserInputEnabled) return false
            lifecycleScope.launch {
                updateUserSettings { it.copy(search = query ?: "") }
                setSearchList(query ?: "")
            }
            return true
        }

        override fun onQueryTextChange(query: String?): Boolean {
            if (!isSearchUserInputEnabled) return false
            lifecycleScope.launch {
                updateUserSettings { it.copy(search = query ?: "") }
                setSearchList(query ?: "")
            }
            return true
        }

        override fun onSearchModeToggle(searchView: SearchView, visible: Boolean) {
            lifecycleScope.launch {
                if (visible) {
                    backPressEnabled.value = true
                    isSearchUserInputEnabled = true
                    val search = getUserSettings().search
                    searchView.setQuery(search, false)
                    val autoCompleteTextView = searchView.seslGetAutoCompleteView()
                    autoCompleteTextView.setText(search)
                    autoCompleteTextView.setSelection(autoCompleteTextView.text.length)
                    setSearchList(search)
                } else {
                    backPressEnabled.value = false
                    isSearchUserInputEnabled = false
                    urls = getUrls()
                    initList()
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
                    }

                    override fun onDrawerClosed(drawerView: View) {
                        backPressEnabled.value = false
                    }

                    override fun onDrawerStateChanged(newState: Int) {}
                }
            )
    }

    fun setSearchList(search: String?) {
        initListJob?.cancel()
        initListJob = lifecycleScope.launch { initList(search = search) }
    }

    private suspend fun initList(newUrls: List<Url>? = null, search: String? = null) {
        if (!this::binding.isInitialized) return
        urls = if (search == null) newUrls ?: getUrls()
        else getSearchList(search)
        initRecycler()
    }

    fun setSelecting(enabled: Boolean) {
        if (enabled) {
            selecting = true
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
            binding.drawerLayoutMain.actionModeBottomMenu.clear()
            binding.drawerLayoutMain.setActionModeMenu(R.menu.menu_select)
            binding.drawerLayoutMain.setActionModeMenuListener { item: MenuItem ->
                Toast.makeText(this, item.title, Toast.LENGTH_SHORT).show()
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
            backPressEnabled.value = true
        } else {
            selecting = false
            for (i in 0 until adapter.itemCount) selected[i] = false
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
            binding.drawerLayoutMain.setActionModeAllSelector(0, true, false)
            binding.drawerLayoutMain.dismissActionMode()
            backPressEnabled.value = false
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
        if (urls.isEmpty()) {
            binding.urlList.visibility = View.GONE
            binding.urlListLottie.cancelAnimation()
            binding.urlListLottie.progress = 0f
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
        }
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
    }

    inner class UrlAdapter internal constructor() : RecyclerView.Adapter<UrlAdapter.ViewHolder>() {
        override fun getItemCount(): Int = urls.size
        override fun getItemViewType(position: Int): Int = 0
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = if (viewType == 0) {
            val inflater = LayoutInflater.from(this@MainActivity)
            val view = inflater.inflate(R.layout.listview_item, parent, false)
            ViewHolder(view, false)
        } else ViewHolder(Separator(this@MainActivity), true)

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.listItemTitle.text = urls[position].shortUrl
            holder.listItemSubtitle1.text = urls[position].longUrl
            holder.listItemSubtitle2.text = urls[position].added.toString()
            holder.listItemImg.setImageResource(dev.oneuiproject.oneui.R.drawable.ic_oui_qr_code)
            holder.parentView.setOnClickListener {
                if (selecting) toggleItemSelected(position)
                else {
                    Toast.makeText(this@MainActivity, holder.listItemTitle.text, Toast.LENGTH_SHORT).show()
                }
            }
            holder.parentView.setOnLongClickListener {
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

            init {
                parentView = itemView as LinearLayout
                listItemImg = itemView.findViewById(R.id.listview_item_img)
                listItemTitle = parentView.findViewById(R.id.list_item_title)
                listItemSubtitle1 = parentView.findViewById(R.id.list_item_subtitle1)
                listItemSubtitle2 = parentView.findViewById(R.id.list_item_subtitle2)
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