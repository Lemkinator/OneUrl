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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatButton
import androidx.core.graphics.scale
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import de.lemke.commonutils.di.DefaultDispatcher
import de.lemke.oneurl.R
import de.lemke.oneurl.data.QRCodeCache
import de.lemke.oneurl.domain.GenerateQRCodeUseCase
import de.lemke.oneurl.domain.model.URL
import dev.oneuiproject.oneui.layout.ToolbarLayout.AllSelectorState
import dev.oneuiproject.oneui.recyclerview.util.MultiSelector
import dev.oneuiproject.oneui.recyclerview.util.MultiSelectorDelegate
import dev.oneuiproject.oneui.utils.SearchHighlighter
import dev.oneuiproject.oneui.widget.SelectableLinearLayout
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class URLAdapter(
    private val context: Context,
    private val qrCodeCache: QRCodeCache,
    private val generateQRCode: GenerateQRCodeUseCase,
    private val scope: CoroutineScope,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    onAllSelectorStateChanged: ((AllSelectorState) -> Unit),
    onBlockActionMode: (() -> Unit),
) : Adapter<URLAdapter.ViewHolder>(),
    MultiSelector<Long> by MultiSelectorDelegate(
        onAllSelectorStateChanged = onAllSelectorStateChanged,
        onBlockActionMode = onBlockActionMode,
        selectionChangePayload = Payload.SELECTION_MODE,
    ) {
    private val searchHighlighter = SearchHighlighter(context)
    private val qrSizePx = context.resources.getDimensionPixelSize(R.dimen.list_item_qr_size)

    private val asyncListDiffer =
        AsyncListDiffer(
            this,
            object : DiffUtil.ItemCallback<URL>() {
                override fun areItemsTheSame(
                    oldItem: URL,
                    newItem: URL,
                ) = oldItem.shortURL == newItem.shortURL

                override fun areContentsTheSame(
                    oldItem: URL,
                    newItem: URL,
                ) = oldItem == newItem
            },
        )

    var onClickItem: ((Int, URL, ViewHolder) -> Unit)? = null

    var onClickItemFavorite: ((Int, URL) -> Unit)? = null

    var onLongClickItem: (() -> Unit)? = null

    var highlightWord = ""
        set(value) {
            if (value != field) {
                field = value
                notifyItemRangeChanged(0, itemCount, Payload.HIGHLIGHT)
            }
        }

    private val currentList: List<URL> get() = asyncListDiffer.currentList

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int) = currentList[position].id

    override fun getItemCount(): Int = currentList.size

    override fun getItemViewType(position: Int): Int = 0

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder =
        ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.listview_item, parent, false),
        ).apply {
            itemView.setOnClickListener {
                bindingAdapterPosition.let { onClickItem?.invoke(it, currentList[it], this@apply) }
            }
            itemView.setOnLongClickListener {
                onLongClickItem?.invoke()
                true
            }
            listItemFav.setOnClickListener {
                bindingAdapterPosition.let { onClickItemFavorite?.invoke(it, currentList[it]) }
            }
        }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else {
            for (payload in payloads.toSet()) {
                when (payload) {
                    Payload.SELECTION_MODE -> holder.bindActionModeAnimate(getItemId(position))
                    Payload.HIGHLIGHT -> holder.bindHighlight(currentList[position])
                }
            }
        }
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        holder.bind(currentList[position])
        holder.bindActionMode(getItemId(position))
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.cancelQrLoad()
    }

    fun submitList(listItems: List<URL>) {
        asyncListDiffer.submitList(listItems)
        updateSelectableIds(listItems.map { it.id })
    }

    fun getItemByPosition(position: Int) = currentList[position]

    // QrEncoder's icon overlay is a fixed dp size regardless of requested QR size; it would swallow
    // a 55dp code, so generate at QrEncoder's default size and downscale instead.
    private fun generateThumbnail(shortURL: String) = generateQRCode(shortURL).scale(qrSizePx, qrSizePx)

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var selectableLayout: SelectableLinearLayout = itemView.findViewById(R.id.listItemSelectableLayout)
        var listItemTitle: TextView = itemView.findViewById(R.id.listItemTitle)
        var listItemSubtitle1: TextView = itemView.findViewById(R.id.listItemSubtitle1)
        var listItemSubtitle2: TextView = itemView.findViewById(R.id.listItemSubtitle2)
        var listItemImg: ImageView = itemView.findViewById(R.id.listItemImg)
        var listItemFav: AppCompatButton = itemView.findViewById(R.id.listItemFav)
        private var qrJob: Job? = null
        private var boundShortURL: String? = null

        fun bind(url: URL) {
            listItemTitle.text = searchHighlighter(url.shortURL, highlightWord)
            listItemSubtitle1.text = searchHighlighter(url.longURL, highlightWord)
            listItemSubtitle2.text =
                searchHighlighter(url.description.ifBlank { url.title }.ifBlank { url.addedFormatMedium }, highlightWord)
            bindQrCode(url.shortURL)
            listItemFav.setCompoundDrawablesRelativeWithIntrinsicBounds(
                null,
                null,
                if (url.favorite) {
                    AppCompatResources.getDrawable(context, dev.oneuiproject.oneui.R.drawable.ic_oui_favorite_on)
                } else {
                    AppCompatResources.getDrawable(context, dev.oneuiproject.oneui.R.drawable.ic_oui_favorite_off)
                },
                null,
            )
        }

        fun cancelQrLoad() {
            qrJob?.cancel()
            qrJob = null
        }

        // Cache hit sets the bitmap immediately - no coroutine, no flash. On miss the ImageView is
        // blanked and the thumbnail is generated off-main; the result is only applied if this
        // holder is still bound to the same shortURL (guards against fast rebind/recycle mid-load).
        private fun bindQrCode(shortURL: String) {
            cancelQrLoad()
            boundShortURL = shortURL
            val cached = qrCodeCache[shortURL, qrSizePx]
            if (cached != null) {
                listItemImg.setImageBitmap(cached)
                return
            }
            listItemImg.setImageBitmap(null)
            qrJob =
                scope.launch {
                    val thumbnail = withContext(defaultDispatcher) { generateThumbnail(shortURL) }
                    qrCodeCache[shortURL, qrSizePx] = thumbnail
                    if (boundShortURL == shortURL) listItemImg.setImageBitmap(thumbnail)
                }
        }

        fun bindActionMode(itemId: Long) {
            selectableLayout.apply {
                isSelectionMode = isActionMode
                setSelected(isSelected(itemId))
            }
        }

        fun bindActionModeAnimate(itemId: Long) {
            selectableLayout.apply {
                isSelectionMode = isActionMode
                setSelectedAnimate(isSelected(itemId))
            }
        }

        fun bindHighlight(url: URL) {
            listItemTitle.text = searchHighlighter(url.shortURL, highlightWord)
            listItemSubtitle1.text = searchHighlighter(url.longURL, highlightWord)
            listItemSubtitle2.text =
                searchHighlighter(url.description.ifBlank { url.title }.ifBlank { url.addedFormatMedium }, highlightWord)
        }
    }

    enum class Payload {
        SELECTION_MODE,
        HIGHLIGHT,
    }
}
