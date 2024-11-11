package de.lemke.oneurl.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatButton
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.SearchHighlighter
import de.lemke.oneurl.domain.model.URL
import dev.oneuiproject.oneui.delegates.MultiSelector
import dev.oneuiproject.oneui.delegates.MultiSelectorDelegate

class URLAdapter(
    private val context: Context
) : RecyclerView.Adapter<URLAdapter.ViewHolder>(),
    MultiSelector<Long> by MultiSelectorDelegate(isSelectable = { true }) {

    private val searchHighlighter = SearchHighlighter(context)

    init {
        setHasStableIds(true)
    }

    private val asyncListDiffer = AsyncListDiffer(this, object : DiffUtil.ItemCallback<URL>() {
        override fun areItemsTheSame(oldItem: URL, newItem: URL) = oldItem == newItem
        override fun areContentsTheSame(oldItem: URL, newItem: URL) = oldItem.contentEquals(newItem)
    })

    var onClickItem: ((Int, URL, ViewHolder) -> Unit)? = null

    var onClickItemFavorite: ((Int, URL) -> Unit)? = null

    var onLongClickItem: (() -> Unit)? = null

    fun submitList(listItems: List<URL>) {
        asyncListDiffer.submitList(listItems)
        updateSelectableIds(listItems.map { it.id })
    }

    var highlightWord = ""
        set(value) {
            if (value != field) {
                field = value
                notifyItemRangeChanged(0, itemCount, Payload.HIGHLIGHT)
            }
        }

    private val currentList: List<URL> get() = asyncListDiffer.currentList

    fun getItemByPosition(position: Int) = currentList[position]

    override fun getItemId(position: Int) = currentList[position].id

    override fun getItemCount(): Int = currentList.size

    override fun getItemViewType(position: Int): Int = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.listview_item, parent, false)
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

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) onBindViewHolder(holder, position)
        else {
            for (payload in payloads.toSet()) {
                when (payload) {
                    Payload.SELECTION_MODE -> holder.bindActionMode(getItemId(position), currentList[position])
                    Payload.HIGHLIGHT -> holder.bindURL(currentList[position])
                }
            }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val url = currentList[position]
        holder.bindURL(url)
        holder.bindActionMode(getItemId(position), url)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val isSeparator = false
        var listItemTitle: TextView = itemView.findViewById(R.id.list_item_title)
        var listItemSubtitle1: TextView = itemView.findViewById(R.id.list_item_subtitle1)
        var listItemSubtitle2: TextView = itemView.findViewById(R.id.list_item_subtitle2)
        var listItemImg: ImageView = itemView.findViewById(R.id.list_item_img)
        var listItemFav: AppCompatButton = itemView.findViewById(R.id.list_item_fav)

        fun bindURL(url: URL) {
            listItemTitle.text = searchHighlighter(url.shortURL, highlightWord)
            listItemSubtitle1.text = searchHighlighter(url.longURL, highlightWord)
            val subtitle2 = url.description.ifBlank { url.title }.ifBlank { url.addedFormatMedium }
            listItemSubtitle2.text = searchHighlighter(subtitle2, highlightWord)
            listItemImg.setImageBitmap(url.qr)
            listItemFav.setCompoundDrawablesRelativeWithIntrinsicBounds(
                null,
                null,
                if (url.favorite) AppCompatResources.getDrawable(context, dev.oneuiproject.oneui.R.drawable.ic_oui_favorite_on)
                else AppCompatResources.getDrawable(context, dev.oneuiproject.oneui.R.drawable.ic_oui_favorite_off),
                null
            )
        }

        fun bindActionMode(itemId: Long, url: URL) {
            if (isActionMode && isSelected(itemId)) listItemImg.setImageResource(R.drawable.url_selected_icon)
            else listItemImg.setImageBitmap(url.qr)
        }
    }

    enum class Payload {
        SELECTION_MODE,
        HIGHLIGHT
    }
}