package de.lemke.oneurl.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.util.SeslRoundedCorner
import androidx.appcompat.util.SeslSubheaderRoundedCorner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.oneurl.R
import de.lemke.oneurl.databinding.ActivityProviderBinding
import de.lemke.oneurl.domain.GetUserSettingsUseCase
import de.lemke.oneurl.domain.UpdateUserSettingsUseCase
import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.domain.model.ShortURLProviderCompanion
import de.lemke.oneurl.domain.setCustomBackPressAnimation
import dev.oneuiproject.oneui.widget.Separator
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ProviderActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProviderBinding
    private lateinit var adapter: ProviderAdapter
    private var provider: List<ShortURLProvider> = ShortURLProviderCompanion.enabled
    private var infoOnly = false

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProviderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.setNavigationButtonOnClickListener { finishAfterTransition() }
        binding.root.setNavigationButtonTooltip(getString(R.string.sesl_navigate_up))
        initRecycler()
        lifecycleScope.launch {
            binding.providerList.scrollToPosition(provider.indexOf(getUserSettings().selectedShortURLProvider))
        }
        setCustomBackPressAnimation(binding.root)
        infoOnly = intent.getBooleanExtra("infoOnly", false)
    }

    private fun initRecycler() {
        binding.providerList.layoutManager = LinearLayoutManager(this)
        adapter = ProviderAdapter()
        binding.providerList.adapter = adapter
        binding.providerList.itemAnimator = null
        binding.providerList.addItemDecoration(ItemDecoration(this))
        binding.providerList.seslSetFastScrollerEnabled(true)
        binding.providerList.seslSetFillBottomEnabled(true)
        binding.providerList.seslSetGoToTopEnabled(true)
        binding.providerList.seslSetLastRoundedCorner(true)
        binding.providerList.seslSetSmoothScrollEnabled(true)
    }

    private fun openInfoDialog(provider: ShortURLProvider) =
        ProviderInfoDialog(provider).show(supportFragmentManager, "providerInfoDialog")

    inner class ProviderAdapter internal constructor() : RecyclerView.Adapter<ProviderAdapter.ViewHolder>() {
        override fun getItemCount(): Int = provider.size
        override fun getItemViewType(position: Int): Int = 0
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = if (viewType == 0) {
            val inflater = LayoutInflater.from(this@ProviderActivity)
            val view = inflater.inflate(R.layout.listview_item_provider, parent, false)
            ViewHolder(view, false)
        } else ViewHolder(Separator(this@ProviderActivity), true)


        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.title.text = provider[position].name
            val infoContents = provider[position].getInfoContents(this@ProviderActivity)
            listOf(holder.icon1, holder.icon2, holder.icon3, holder.icon4).forEachIndexed { index, iconView ->
                if (index < infoContents.size) {
                    iconView.setImageResource(infoContents[index].icon)
                    iconView.visibility = View.VISIBLE
                } else iconView.visibility = View.GONE
            }
            holder.parentView.setOnClickListener {
                if (infoOnly) openInfoDialog(provider[position])
                else lifecycleScope.launch {
                    updateUserSettings { it.copy(selectedShortURLProvider = provider[position]) }
                    finishAfterTransition()
                }
            }
            holder.iconLayout.setOnClickListener { openInfoDialog(provider[position]) }
            holder.parentView.setOnLongClickListener {
                openInfoDialog(provider[position])
                true
            }
        }

        inner class ViewHolder internal constructor(itemView: View, var isSeparator: Boolean) : RecyclerView.ViewHolder(itemView) {
            var parentView: LinearLayout = itemView as LinearLayout
            var title: TextView = parentView.findViewById(R.id.providerTitle)
            var icon1: ImageView = parentView.findViewById(R.id.providerIcon1)
            var icon2: ImageView = parentView.findViewById(R.id.providerIcon2)
            var icon3: ImageView = parentView.findViewById(R.id.providerIcon3)
            var icon4: ImageView = parentView.findViewById(R.id.providerIcon4)
            var iconLayout: LinearLayout = parentView.findViewById(R.id.providerIconLayout)
        }
    }

    @SuppressLint("PrivateResource")
    private inner class ItemDecoration(context: Context) : RecyclerView.ItemDecoration() {
        private val divider: Drawable?
        private val roundedCorner: SeslSubheaderRoundedCorner

        init {
            val outValue = TypedValue()
            context.theme.resolveAttribute(androidx.appcompat.R.attr.isLightTheme, outValue, true)
            divider = AppCompatResources.getDrawable(
                context,
                if (outValue.data == 0) androidx.appcompat.R.drawable.sesl_list_divider_dark
                else androidx.appcompat.R.drawable.sesl_list_divider_light
            )!!
            roundedCorner = SeslSubheaderRoundedCorner(this@ProviderActivity)
            roundedCorner.roundedCorners = SeslRoundedCorner.ROUNDED_CORNER_ALL
        }

        override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            super.onDraw(c, parent, state)
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                val holder = binding.providerList.getChildViewHolder(child) as ProviderAdapter.ViewHolder
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
                val holder = binding.providerList.getChildViewHolder(child) as ProviderAdapter.ViewHolder
                if (holder.isSeparator) roundedCorner.drawRoundedCorner(child, c)
            }
        }
    }
}