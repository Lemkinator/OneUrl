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

package de.lemke.oneurl

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import leakcanary.AppWatcher
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class DebugToolsTest {
    @Test
    fun openLeakCanary_startsActivity() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        if (!AppWatcher.isInstalled) {
            AppWatcher.manualInstall(app)
        }
        openLeakCanary(app)
        assertNotNull(shadowOf(app).nextStartedActivity)
    }
}
