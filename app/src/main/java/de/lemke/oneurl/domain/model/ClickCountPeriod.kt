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

package de.lemke.oneurl.domain.model

import androidx.annotation.StringRes
import de.lemke.oneurl.R

/** The span of time a provider's click count covers; [labelRes] names the span next to the count. */
enum class ClickCountPeriod(
    @StringRes val labelRes: Int,
) {
    AllTime(R.string.visits_),
    Last90Days(R.string.visits_last_3_months),
}
