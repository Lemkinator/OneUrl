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

package de.lemke.oneurl.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface NetworkMonitor {
    /** True when the active network offers internet access that the system has validated. */
    fun hasValidatedInternet(): Boolean
}

class DefaultNetworkMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : NetworkMonitor {
    override fun hasValidatedInternet(): Boolean {
        val connectivityManager: ConnectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork) ?: return false
        return capabilities.hasCapability(NET_CAPABILITY_INTERNET) && capabilities.hasCapability(NET_CAPABILITY_VALIDATED)
    }
}
