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

import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import de.lemke.commonutils.ui.utils.onSingleLaunchClick
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.model.ShortURLProvider

/** Binds a provider name and its feature icons into any layout that holds the `provider*` row views. */
class ProviderRow(root: View) {
    private val title: TextView = root.findViewById(R.id.providerTitle)
    private val icons: List<ImageView> =
        listOf(R.id.providerIcon1, R.id.providerIcon2, R.id.providerIcon3, R.id.providerIcon4).map { root.findViewById(it) }
    private val iconLayout: LinearLayout = root.findViewById(R.id.providerIconLayout)

    fun bind(
        provider: ShortURLProvider,
        onInfoClick: () -> Unit,
    ) {
        title.text = provider.name
        val infoContents = provider.getInfoContents(title.context)
        icons.forEachIndexed { index, iconView ->
            if (index < infoContents.size) {
                iconView.setImageResource(infoContents[index].icon)
                iconView.isVisible = true
            } else {
                iconView.isVisible = false
            }
        }
        iconLayout.contentDescription = title.context.providerInfoDescription(provider.name, infoContents.take(icons.size))
        iconLayout.onSingleLaunchClick { onInfoClick() }
    }
}
