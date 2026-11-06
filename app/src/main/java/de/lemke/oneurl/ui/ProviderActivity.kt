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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.ui.utils.collectState
import de.lemke.commonutils.ui.utils.onSingleLaunchClick
import de.lemke.commonutils.ui.utils.prepareActivityTransformationTo
import de.lemke.commonutils.ui.utils.setCustomBackAnimation
import de.lemke.commonutils.ui.utils.transformToActivity
import de.lemke.oneurl.R
import de.lemke.oneurl.databinding.ActivityProviderBinding
import de.lemke.oneurl.ui.ProviderInfoBottomSheet.Companion.showProviderInfoBottomSheet
import dev.oneuiproject.oneui.recyclerview.ktx.enableCoreSeslFeatures
import dev.oneuiproject.oneui.utils.ItemDecorRule.ALL
import dev.oneuiproject.oneui.utils.ItemDecorRule.NONE
import dev.oneuiproject.oneui.utils.SemItemDecoration
import dev.oneuiproject.oneui.R as oneuiR

@AndroidEntryPoint
class ProviderActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProviderBinding
    private val viewModel: ProviderViewModel by viewModels()
    private val providerAdapter = ProviderAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        prepareActivityTransformationTo()
        super.onCreate(savedInstanceState)
        if (ProviderScreenMode.fromName(intent.getStringExtra(KEY_MODE)) == null) {
            Log.w(TAG, "Finishing: the intent carries no valid provider screen mode")
            finish()
            return
        }
        binding = ActivityProviderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setCustomBackAnimation(binding.root)
        initRecycler()
        collectState(viewModel.state) { render(it) }
        collectState(viewModel.navigation, minActiveState = RESUMED) { if (it is ProviderNavigation.Request) navigate(it) }
    }

    private fun render(state: ProviderUiState) {
        binding.root.setTitle(getString(state.mode.title))
        providerAdapter.submitList(state.items)
        state.scrollToPosition?.let {
            binding.providerList.scrollToPosition(it)
            viewModel.onScrolledToSelected()
        }
    }

    private fun navigate(request: ProviderNavigation.Request) {
        when (request) {
            is ProviderNavigation.ShowInfo -> showProviderInfoBottomSheet(request.provider)
            ProviderNavigation.Finish -> finishAfterTransition()
        }
        viewModel.onNavigationHandled(request)
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

    companion object {
        const val KEY_MODE = "providerScreenMode"
        private const val TAG = "ProviderActivity"
        private const val TRANSITION_NAME = "ProviderTransformation"

        fun intent(
            context: Context,
            mode: ProviderScreenMode,
        ): Intent = Intent(context, ProviderActivity::class.java).putExtra(KEY_MODE, mode.name)

        fun View.transformToProviderActivity(mode: ProviderScreenMode): Boolean =
            transformToActivity(intent(context, mode), TRANSITION_NAME)
    }

    private object ProviderDiffCallback : DiffUtil.ItemCallback<ProviderItem>() {
        override fun areItemsTheSame(
            oldItem: ProviderItem,
            newItem: ProviderItem,
        ): Boolean = oldItem.provider.name == newItem.provider.name

        override fun areContentsTheSame(
            oldItem: ProviderItem,
            newItem: ProviderItem,
        ): Boolean = oldItem == newItem
    }

    inner class ProviderAdapter : ListAdapter<ProviderItem, ProviderAdapter.ViewHolder>(ProviderDiffCallback) {
        override fun getItemViewType(position: Int): Int = 0

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): ViewHolder = ViewHolder(LayoutInflater.from(this@ProviderActivity).inflate(R.layout.listview_item_provider, parent, false))

        override fun onBindViewHolder(
            holder: ViewHolder,
            position: Int,
        ) {
            val (provider, marked) = getItem(position)
            holder.row.bind(provider) { viewModel.onProviderInfoClick(provider) }
            holder.markSelected(provider.name, marked)
            holder.itemView.onSingleLaunchClick { viewModel.onProviderClick(provider) }
            holder.itemView.setOnLongClickListener { viewModel.onProviderInfoClick(provider).let { true } }
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val row = ProviderRow(itemView)
            private val title: TextView = itemView.findViewById(R.id.providerTitle)

            fun markSelected(
                name: String,
                marked: Boolean,
            ) {
                title.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, if (marked) oneuiR.drawable.ic_oui_checkbox_checked else 0, 0)
                title.contentDescription = if (marked) getString(R.string.selected_provider_row, name) else null
            }
        }
    }
}
