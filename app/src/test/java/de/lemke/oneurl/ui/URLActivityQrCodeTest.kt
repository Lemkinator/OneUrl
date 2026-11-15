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

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.ImageView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import de.lemke.commonutils.HiltTestRule
import de.lemke.commonutils.idleMainLooper
import de.lemke.oneurl.R
import de.lemke.oneurl.data.FakeQRCodeRenderer
import de.lemke.oneurl.data.FakeQRCodeRenderer.Render
import de.lemke.oneurl.data.FakeURLRepository
import de.lemke.oneurl.data.QRCodeCache
import de.lemke.oneurl.data.QRCodeRenderer
import de.lemke.oneurl.data.URLRepository
import de.lemke.oneurl.di.QRCodeRendererModule
import de.lemke.oneurl.di.URLRepositoryModule
import de.lemke.oneurl.domain.model.URL
import de.lemke.oneurl.domain.testUrl
import de.lemke.oneurl.ui.ShortURLQRCodeViewModel.Companion.KEY_SHORT_URL
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
@UninstallModules(URLRepositoryModule::class, QRCodeRendererModule::class)
class URLActivityQrCodeTest {
    @get:Rule(order = 0)
    val hiltRule = HiltTestRule(this)

    private val url: URL = testUrl(shortURL = "https://da.gd/seeded1")
    private val rows = FakeURLRepository(listOf(url))
    private val lookupGate = CompletableDeferred<Unit>()
    private val qrCode: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private val renderer = FakeQRCodeRenderer(qrCode, placeholder = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888))

    @BindValue
    @JvmField
    val urlRepository: URLRepository =
        object : URLRepository by rows {
            override suspend fun getURL(shortURL: String): URL? {
                lookupGate.await()
                return rows.getURL(shortURL)
            }
        }

    @BindValue
    @JvmField
    val qrCodeRenderer: QRCodeRenderer = renderer

    @Inject
    lateinit var qrCodeCache: QRCodeCache

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun `the qr code shows and its actions enable only once the url is loaded`() {
        launch(url.shortURL).use { scenario ->
            scenario.onActivity { activity ->
                activity.qrImage().drawable shouldBe null
                activity.findViewById<View>(R.id.url_qr_save_button).isEnabled.shouldBeFalse()
                activity.findViewById<View>(R.id.url_qr_share_button).isEnabled.shouldBeFalse()
                renderer.renders shouldBe emptyList()
            }

            lookupGate.complete(Unit)
            idleMainLooper()

            scenario.onActivity { activity ->
                (activity.qrImage().drawable as BitmapDrawable).bitmap shouldBe qrCode
                activity.findViewById<View>(R.id.url_qr_save_button).isEnabled.shouldBeTrue()
                activity.findViewById<View>(R.id.url_qr_share_button).isEnabled.shouldBeTrue()
                renderer.renders shouldBe listOf(Render("https://da.gd/seeded1", null))
                qrCodeCache["https://da.gd/seeded1"] shouldBe qrCode
            }
        }
    }

    @Test
    fun `a missing url generates and caches no qr code`() {
        lookupGate.complete(Unit)

        launch("https://da.gd/missing").use { scenario ->
            scenario.onActivity { activity ->
                activity.qrImage().drawable shouldBe null
                activity.findViewById<View>(R.id.url_qr_save_button).isEnabled.shouldBeFalse()
                activity.findViewById<View>(R.id.url_qr_share_button).isEnabled.shouldBeFalse()
                activity.isFinishing.shouldBeTrue()
                renderer.renders shouldBe emptyList()
                qrCodeCache["https://da.gd/missing"] shouldBe null
            }
        }
    }

    private fun launch(shortURL: String): ActivityScenario<URLActivity> =
        ActivityScenario
            .launch<URLActivity>(
                Intent(ApplicationProvider.getApplicationContext(), URLActivity::class.java).putExtra(KEY_SHORT_URL, shortURL),
            ).also { idleMainLooper() }

    private fun URLActivity.qrImage(): ImageView = findViewById(R.id.url_qr_imageview)
}
