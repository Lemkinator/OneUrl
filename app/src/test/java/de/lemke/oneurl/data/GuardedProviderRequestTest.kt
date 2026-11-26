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

import android.app.Activity
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.android.volley.Header
import com.android.volley.Network
import com.android.volley.NetworkResponse
import com.android.volley.NoConnectionError
import com.android.volley.RequestQueue
import com.android.volley.toolbox.NoCache
import de.lemke.commonutils.idleMainLooper
import de.lemke.oneurl.domain.model.Dagd
import de.lemke.oneurl.domain.model.GenerateURLError
import de.lemke.oneurl.domain.model.Gg
import de.lemke.oneurl.domain.model.Shrtlnk
import de.lemke.oneurl.ui.configureFor
import io.kotest.matchers.shouldBe
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.inject.Provider
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.appcompat.R as appcompatR
import de.lemke.commonutils.R as commonutilsR

/** Sends real provider requests through [DefaultVolleyQueue] over a Volley queue whose network answers from a fixed map. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class GuardedProviderRequestTest {
    private val longURL = "https://example.com"
    private val requestQueues = mutableListOf<RequestQueue>()
    private val errors = LinkedBlockingQueue<GenerateURLError>()

    @After
    fun stopRequestQueues() = requestQueues.forEach(RequestQueue::stop)

    private fun queueAnswering(network: Network): VolleyQueue {
        val requestQueue =
            RequestQueue(NoCache(), network, 1, GuardedDelivery(Handler(Looper.getMainLooper()))).apply { start() }
        requestQueues += requestQueue
        return DefaultVolleyQueue(Provider { requestQueue })
    }

    private fun queueAnswering(responses: Map<String, NetworkResponse>) = queueAnswering(Network { responses.getValue(it.url) })

    private fun awaitError() = awaitResult(errors)

    private fun <T : Any> awaitResult(results: LinkedBlockingQueue<T>): T {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(DELIVERY_TIMEOUT_SECONDS)
        while (System.nanoTime() < deadline) {
            idleMainLooper()
            results.poll(POLL_MILLIS, TimeUnit.MILLISECONDS)?.let { return it }
        }
        error("no result within $DELIVERY_TIMEOUT_SECONDS seconds")
    }

    private fun dialogTexts(error: GenerateURLError): Pair<String?, String?> {
        val activity =
            Robolectric
                .buildActivity(Activity::class.java)
                .setup()
                .get()
                .apply { setTheme(commonutilsR.style.CommonUtils_AppTheme) }
        val dialog =
            AlertDialog
                .Builder(activity)
                .apply { configureFor(error) }
                .create()
                .apply { show() }
        idleMainLooper()
        return dialog.findViewById<TextView>(appcompatR.id.alertTitle)?.text?.toString() to
            dialog.findViewById<TextView>(android.R.id.message)?.text?.toString()
    }

    @Test
    fun `a da-gd create response whose success callback throws shows the unknown error dialog`() {
        val queue = queueAnswering(mapOf("https://da.gd/shorten?url=$longURL" to NetworkResponse("https://da.gd/abc".toByteArray())))

        queue.add(Dagd.getCreateRequest(queue, longURL, "", { error("success boom") }, errors::add))

        val error = awaitError()
        error shouldBe GenerateURLError.Unknown()
        dialogTexts(error) shouldBe ("Error" to "Unknown error\nIf this keeps happening, try another provider.")
    }

    @Test
    fun `a gg create response whose success callback throws shows the unknown error dialog`() {
        val queue =
            queueAnswering(
                mapOf(
                    "https://gg.gg/check" to NetworkResponse("ok".toByteArray()),
                    "https://gg.gg/create" to NetworkResponse("https://gg.gg/abc".toByteArray()),
                ),
            )

        queue.add(Gg.getCreateRequest(queue, longURL, "abc", { error("success boom") }, errors::add))

        val error = awaitError()
        error shouldBe GenerateURLError.Unknown()
        dialogTexts(error) shouldBe ("Error" to "Unknown error\nIf this keeps happening, try another provider.")
    }

    @Test
    fun `a gg check response whose error callback throws still creates the alias and delivers it`() {
        val queue =
            queueAnswering(
                mapOf(
                    "https://gg.gg/check" to NetworkResponse("Link with this path already exists. Choose another path.".toByteArray()),
                    "https://gg.gg/create" to NetworkResponse("https://gg.gg/abc".toByteArray()),
                ),
            )
        val shortURLs = LinkedBlockingQueue<String>()

        queue.add(Gg.getCreateRequest(queue, longURL, "abc", shortURLs::add) { error("error boom") })

        awaitResult(shortURLs) shouldBe "https://gg.gg/abc"
    }

    @Test
    fun `a shrtlnk redirect response whose success callback throws shows the unknown error dialog`() {
        val redirect = NetworkResponse(204, ByteArray(0), false, 0L, listOf(Header("X-Remix-Redirect", "/new-link-added?key=h1aja4")))
        val queue = queueAnswering(mapOf(Shrtlnk.apiURL to redirect))

        queue.add(Shrtlnk.getCreateRequest(queue, longURL, "", { error("success boom") }, errors::add))

        val error = awaitError()
        error shouldBe GenerateURLError.Unknown()
        dialogTexts(error) shouldBe ("Error" to "Unknown error\nIf this keeps happening, try another provider.")
    }

    @Test
    fun `a shrtlnk response without a redirect shows the unknown error dialog`() {
        val queue =
            queueAnswering(mapOf(Shrtlnk.apiURL to NetworkResponse(204, ByteArray(0), false, 0L, listOf(Header("Server", "Vercel")))))

        queue.add(Shrtlnk.getCreateRequest(queue, longURL, "", { error("unexpected success") }, errors::add))

        val error = awaitError()
        error shouldBe GenerateURLError.Unknown()
        dialogTexts(error) shouldBe ("Error" to "Unknown error\nIf this keeps happening, try another provider.")
    }

    @Test
    fun `a shrtlnk request without a connection shows the offline dialog`() {
        val queue = queueAnswering(Network { throw NoConnectionError() })

        queue.add(Shrtlnk.getCreateRequest(queue, longURL, "", { error("unexpected success") }, errors::add))

        val error = awaitError()
        error shouldBe GenerateURLError.ServiceOffline
        dialogTexts(error) shouldBe ("Error" to "The service of this provider is offline. Please use another provider.")
    }

    @Test
    fun `a shrtlnk error callback that throws on a failed request does not stop the queue`() {
        val queue = queueAnswering(Network { throw NoConnectionError() })
        queue.add(Shrtlnk.getCreateRequest(queue, longURL, "", { error("unexpected success") }) { error("error boom") })
        queue.add(Shrtlnk.getCreateRequest(queue, longURL, "", { error("unexpected success") }, errors::add))

        awaitError() shouldBe GenerateURLError.ServiceOffline
    }

    private companion object {
        const val DELIVERY_TIMEOUT_SECONDS = 5L
        const val POLL_MILLIS = 10L
    }
}
