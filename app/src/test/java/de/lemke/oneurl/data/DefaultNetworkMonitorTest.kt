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

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class DefaultNetworkMonitorTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val monitor = DefaultNetworkMonitor(context)

    private fun activeNetworkCapabilities(vararg capabilities: Int) {
        val networkCapabilities = ShadowNetworkCapabilities.newInstance()
        capabilities.forEach { shadowOf(networkCapabilities).addCapability(it) }
        shadowOf(connectivityManager).setNetworkCapabilities(connectivityManager.activeNetwork, networkCapabilities)
    }

    @Test
    fun `no active network has no validated internet`() {
        shadowOf(connectivityManager).setDefaultNetworkActive(false)
        shadowOf(connectivityManager).setActiveNetworkInfo(null)

        monitor.hasValidatedInternet() shouldBe false
    }

    @Test
    fun `a network without the internet capability has no validated internet`() {
        activeNetworkCapabilities(NET_CAPABILITY_VALIDATED)

        monitor.hasValidatedInternet() shouldBe false
    }

    @Test
    fun `an unvalidated internet network has no validated internet`() {
        activeNetworkCapabilities(NET_CAPABILITY_INTERNET)

        monitor.hasValidatedInternet() shouldBe false
    }

    @Test
    fun `a validated internet network has validated internet`() {
        activeNetworkCapabilities(NET_CAPABILITY_INTERNET, NET_CAPABILITY_VALIDATED)

        monitor.hasValidatedInternet() shouldBe true
    }
}
