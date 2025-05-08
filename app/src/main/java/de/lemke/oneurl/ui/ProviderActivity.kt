package de.lemke.oneurl.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.prepareActivityTransformationTo
import de.lemke.commonutils.setCustomBackPressAnimation
import de.lemke.oneurl.R
import de.lemke.oneurl.databinding.ActivityProviderBinding
import de.lemke.oneurl.domain.GetUserSettingsUseCase
import de.lemke.oneurl.domain.UpdateUserSettingsUseCase
import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.domain.model.ShortURLProviderCompanion
import de.lemke.oneurl.ui.ProviderInfoBottomSheet.Companion.showProviderInfoBottomSheet
import dev.oneuiproject.oneui.ktx.enableCoreSeslFeatures
import dev.oneuiproject.oneui.utils.ItemDecorRule.ALL
import dev.oneuiproject.oneui.utils.ItemDecorRule.NONE
import dev.oneuiproject.oneui.utils.SemItemDecoration
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ProviderActivity : AppCompatActivity() {
    companion object {
        const val KEY_SELECT_PROVIDER = "key_select_provider"
    }

    private lateinit var binding: ActivityProviderBinding
    private var provider: List<ShortURLProvider> = ShortURLProviderCompanion.enabled
    private var selectProvider = false

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        prepareActivityTransformationTo()
        super.onCreate(savedInstanceState)
        binding = ActivityProviderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setCustomBackPressAnimation(binding.root)
        initRecycler()
        selectProvider = intent.getBooleanExtra(KEY_SELECT_PROVIDER, false)
        lifecycleScope.launch {
            binding.providerList.scrollToPosition(provider.indexOf(getUserSettings().selectedShortURLProvider))
        }
    }

    private fun initRecycler() {
        binding.providerList.apply {
            layoutManager = LinearLayoutManager(this@ProviderActivity)
            adapter = ProviderAdapter()
            itemAnimator = null
            addItemDecoration(SemItemDecoration(this@ProviderActivity, dividerRule = ALL, subHeaderRule = NONE))
            enableCoreSeslFeatures()
        }
    }

    inner class ProviderAdapter internal constructor() : RecyclerView.Adapter<ProviderAdapter.ViewHolder>() {
        override fun getItemCount(): Int = provider.size
        override fun getItemViewType(position: Int): Int = 0
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(LayoutInflater.from(this@ProviderActivity).inflate(R.layout.listview_item_provider, parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.title.text = provider[position].name
            val infoContents = provider[position].getInfoContents(this@ProviderActivity)
            listOf(holder.icon1, holder.icon2, holder.icon3, holder.icon4).forEachIndexed { index, iconView ->
                if (index < infoContents.size) {
                    iconView.setImageResource(infoContents[index].icon)
                    iconView.isVisible = true
                } else iconView.isVisible = false
            }
            holder.parentView.setOnClickListener {
                if (!selectProvider) showProviderInfoBottomSheet(provider[position])
                else lifecycleScope.launch {
                    updateUserSettings { it.copy(selectedShortURLProvider = provider[position]) }
                    finishAfterTransition()
                }
            }
            holder.iconLayout.setOnClickListener { showProviderInfoBottomSheet(provider[position]) }
            holder.parentView.setOnLongClickListener { showProviderInfoBottomSheet(provider[position]).let { true } }
        }

        inner class ViewHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {
            var parentView: LinearLayout = itemView as LinearLayout
            var title: TextView = parentView.findViewById(R.id.providerTitle)
            var icon1: ImageView = parentView.findViewById(R.id.providerIcon1)
            var icon2: ImageView = parentView.findViewById(R.id.providerIcon2)
            var icon3: ImageView = parentView.findViewById(R.id.providerIcon3)
            var icon4: ImageView = parentView.findViewById(R.id.providerIcon4)
            var iconLayout: LinearLayout = parentView.findViewById(R.id.providerIconLayout)
        }
    }
}