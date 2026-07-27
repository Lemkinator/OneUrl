/*
 * Copyright 2023-2026 Leonard Lemke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.lemke.oneurl.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.ui.utils.collectEvents
import de.lemke.commonutils.ui.utils.collectState
import de.lemke.commonutils.ui.utils.prepareActivityTransformationTo
import de.lemke.commonutils.ui.utils.setCustomBackAnimation
import de.lemke.oneurl.R
import de.lemke.oneurl.databinding.ActivityProviderBinding
import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.ui.ProviderInfoBottomSheet.Companion.showProviderInfoBottomSheet
import dev.oneuiproject.oneui.recyclerview.ktx.enableCoreSeslFeatures
import dev.oneuiproject.oneui.utils.ItemDecorRule.ALL
import dev.oneuiproject.oneui.utils.ItemDecorRule.NONE
import dev.oneuiproject.oneui.utils.SemItemDecoration

@AndroidEntryPoint
class ProviderActivity : AppCompatActivity() {
    companion object {
        const val KEY_SELECT_PROVIDER = "key_select_provider"
    }

    private lateinit var binding: ActivityProviderBinding
    private val viewModel: ProviderViewModel by viewModels()
    private val providerAdapter = ProviderAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        prepareActivityTransformationTo()
        super.onCreate(savedInstanceState)
        binding = ActivityProviderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setCustomBackAnimation(binding.root)
        initRecycler()
        collectState()
        collectEvents()
    }

    private fun collectState() =
        collectState(viewModel.state) { state ->
            providerAdapter.updateProviders(state.providers)
        }

    private fun collectEvents() =
        collectEvents(viewModel.events) { event ->
            when (event) {
                is ProviderEvent.Finish -> finishAfterTransition()
                is ProviderEvent.ShowInfo -> showProviderInfoBottomSheet(event.provider)
                is ProviderEvent.ScrollToSelected -> binding.providerList.scrollToPosition(event.position)
            }
        }

    private fun initRecycler() {
        binding.providerList.apply {
            layoutManager = LinearLayoutManager(this@ProviderActivity)
            adapter = providerAdapter
            itemAnimator = null
            addItemDecoration(SemItemDecoration(this@ProviderActivity, dividerRule = ALL, subHeaderRule = NONE))
            enableCoreSeslFeatures()
        }
    }

    inner class ProviderAdapter : RecyclerView.Adapter<ProviderAdapter.ViewHolder>() {
        private var providers: List<ShortURLProvider> = emptyList()

        fun updateProviders(newProviders: List<ShortURLProvider>) {
            providers = newProviders
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = providers.size

        override fun getItemViewType(position: Int): Int = 0

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): ViewHolder = ViewHolder(LayoutInflater.from(this@ProviderActivity).inflate(R.layout.listview_item_provider, parent, false))

        override fun onBindViewHolder(
            holder: ViewHolder,
            position: Int,
        ) {
            val provider = providers[position]
            holder.title.text = provider.name
            val infoContents = provider.getInfoContents(this@ProviderActivity)
            listOf(holder.icon1, holder.icon2, holder.icon3, holder.icon4).forEachIndexed { index, iconView ->
                if (index < infoContents.size) {
                    iconView.setImageResource(infoContents[index].icon)
                    iconView.isVisible = true
                } else {
                    iconView.isVisible = false
                }
            }
            holder.parentView.setOnClickListener { viewModel.onProviderClick(provider) }
            holder.iconLayout.setOnClickListener { viewModel.onProviderInfoClick(provider) }
            holder.parentView.setOnLongClickListener { viewModel.onProviderInfoClick(provider).let { true } }
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val parentView: LinearLayout = itemView as LinearLayout
            val title: TextView = parentView.findViewById(R.id.providerTitle)
            val icon1: ImageView = parentView.findViewById(R.id.providerIcon1)
            val icon2: ImageView = parentView.findViewById(R.id.providerIcon2)
            val icon3: ImageView = parentView.findViewById(R.id.providerIcon3)
            val icon4: ImageView = parentView.findViewById(R.id.providerIcon4)
            val iconLayout: LinearLayout = parentView.findViewById(R.id.providerIconLayout)
        }
    }
}
