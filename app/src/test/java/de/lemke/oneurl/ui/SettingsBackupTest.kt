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

import android.app.Activity
import android.content.Intent
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.HiltTestRule
import de.lemke.commonutils.RecordingDocumentsProvider
import de.lemke.commonutils.idleMainLooper
import de.lemke.commonutils.receivePickerResult
import de.lemke.commonutils.ui.activity.CommonUtilsSettingsActivity
import de.lemke.commonutils.ui.utils.setupCommonUtilsSettingsActivity
import de.lemke.oneurl.data.URLRepository
import de.lemke.oneurl.domain.model.Dagd
import de.lemke.oneurl.domain.model.URL
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import de.lemke.commonutils.R as commonutilsR

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class SettingsBackupTest {
    @get:Rule(order = 0)
    val hiltRule = HiltTestRule(this)

    @Inject
    lateinit var urlRepository: URLRepository

    private val documents = RecordingDocumentsProvider.create(ApplicationProvider.getApplicationContext())
    private val older = url("https://da.gd/older", ZonedDateTime.of(2026, 10, 26, 9, 0, 0, 0, ZoneId.systemDefault()))
    private val newer = url("https://da.gd/newer", ZonedDateTime.of(2026, 10, 27, 14, 30, 0, 0, ZoneId.systemDefault()))

    @Before
    fun setUp() {
        hiltRule.inject()
        setupCommonUtilsSettingsActivity(commonutilsR.xml.preferences_backup, relativeLinksCard = false)
    }

    @After
    fun resetSettingsActivity() {
        CommonUtilsSettingsActivity.preferences = emptyList()
        CommonUtilsSettingsActivity.relativeLinksCard = true
    }

    private fun url(
        shortURL: String,
        added: ZonedDateTime,
    ) = URL(shortURL, "https://example.com/$shortURL", Dagd, favorite = false, title = "title", description = "description", added = added)

    private fun storedURLs(): List<URL> = runBlocking { urlRepository.observeURLs().first() }

    private fun launchSettings(block: (ActivityScenario<CommonUtilsSettingsActivity>) -> Unit) =
        ActivityScenario.launch(CommonUtilsSettingsActivity::class.java).use(block)

    private fun ActivityScenario<*>.pickDocument(): Intent {
        lateinit var picker: Intent
        onActivity { picker = it.receivePickerResult(Activity.RESULT_OK, Intent().setData(documents.uri)) }
        idleMainLooper()
        return picker
    }

    private fun latestDialog(): AlertDialog = ShadowDialog.getLatestDialog() as AlertDialog

    private fun AlertDialog.message(): String = findViewById<TextView>(android.R.id.message)!!.text.toString()

    private fun AlertDialog.click(button: Int) {
        getButton(button).performClick()
        idleMainLooper()
    }

    private fun ActivityScenario<*>.exportBackup() {
        onView(withText("Export backup")).perform(click())
        pickDocument()
        latestDialog().message() shouldBe "Backup exported"
        latestDialog().click(AlertDialog.BUTTON_POSITIVE)
    }

    private fun ActivityScenario<*>.importBackup() {
        onView(withText("Import backup")).perform(click())
        pickDocument()
    }

    @Test
    fun `export opens the create-document picker for an octet stream under the oneurl extension`() {
        launchSettings { scenario ->
            onView(withText("Export backup")).perform(click())

            lateinit var picker: Intent
            scenario.onActivity { picker = it.receivePickerResult(Activity.RESULT_CANCELED, null) }

            picker.action shouldBe Intent.ACTION_CREATE_DOCUMENT
            picker.type shouldBe "application/octet-stream"
            picker.getStringExtra(Intent.EXTRA_TITLE)!! shouldMatch Regex(".+_backup_\\d{4}_\\d{2}_\\d{2}_\\d{2}_\\d{2}_\\d{2}\\.oneurl")
        }
    }

    @Test
    fun `export then merge import into a cleared database restores the urls`() {
        runBlocking {
            urlRepository.addURL(older)
            urlRepository.addURL(newer)
        }
        launchSettings { scenario ->
            scenario.exportBackup()
            runBlocking { urlRepository.deleteURLs(listOf(older, newer)) }
            storedURLs() shouldBe emptyList()

            scenario.importBackup()
            latestDialog().click(AlertDialog.BUTTON_POSITIVE)

            storedURLs() shouldContainExactly listOf(newer, older)
            latestDialog().message() shouldBe "Backup imported: 2 added, 0 updated, 0 kept, 0 broken entries skipped."
        }
    }

    @Test
    fun `replace import shows the warning and then replaces`() {
        runBlocking {
            urlRepository.addURL(older)
            urlRepository.addURL(newer)
        }
        launchSettings { scenario ->
            scenario.exportBackup()
            val localOnly = url("https://da.gd/local", ZonedDateTime.of(2026, 10, 28, 8, 0, 0, 0, ZoneId.systemDefault()))
            runBlocking {
                urlRepository.deleteURLs(listOf(older, newer))
                urlRepository.addURL(localOnly)
            }

            scenario.importBackup()
            latestDialog().click(AlertDialog.BUTTON_NEUTRAL)

            latestDialog().message() shouldBe
                "Attention: This deletes the current data of the parts in this backup and replaces it with the backup. This cannot be undone."
            storedURLs() shouldContainExactly listOf(localOnly)

            latestDialog().click(AlertDialog.BUTTON_POSITIVE)

            storedURLs() shouldContainExactly listOf(newer, older)
            latestDialog().message() shouldBe "Backup imported: 2 entries restored, 1 previous entry removed, 0 broken entries skipped."
        }
    }
}
