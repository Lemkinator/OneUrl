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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lemke.oneurl.data.StoredTlyKey
import de.lemke.oneurl.data.TlyKeyStore
import de.lemke.oneurl.data.TlyKeyWrite
import de.lemke.oneurl.domain.model.TlyApiKey
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class ProviderInfoViewModel @Inject constructor(
    private val tlyKeys: TlyKeyStore,
) : ViewModel() {
    /** The key text as the user sees it, or null until the stored key is read. */
    val tlyKey: StateFlow<String?>
        field = MutableStateFlow<String?>(null)

    /** True after the store refused a write, until the screen reports it shown. */
    val tlyKeySaveFailed: StateFlow<Boolean>
        field = MutableStateFlow(false)

    private val writeLock = Mutex()

    init {
        viewModelScope.launch {
            tlyKey.value =
                when (val stored = tlyKeys.get()) {
                    is StoredTlyKey.Key -> stored.apiKey.token
                    StoredTlyKey.None, StoredTlyKey.Unavailable -> ""
                }
        }
    }

    fun onTlyKeyChanged(text: String) {
        if (text == tlyKey.value) return
        tlyKey.value = text
        viewModelScope.launch {
            when (writeLock.withLock { tlyKeys.set(TlyApiKey.ofOrNull(text)) }) {
                TlyKeyWrite.Saved -> Unit
                TlyKeyWrite.Failed -> tlyKeySaveFailed.value = true
            }
        }
    }

    fun onTlyKeySaveFailedHandled() {
        tlyKeySaveFailed.value = false
    }
}
