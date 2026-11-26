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

package de.lemke.oneurl.di

import android.os.Handler
import android.os.Looper
import com.android.volley.RequestQueue
import com.android.volley.toolbox.BasicNetwork
import com.android.volley.toolbox.HurlStack
import com.android.volley.toolbox.NoCache
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.lemke.oneurl.data.DefaultNetworkMonitor
import de.lemke.oneurl.data.DefaultPageTitleFetcher
import de.lemke.oneurl.data.DefaultShortURLClient
import de.lemke.oneurl.data.DefaultUrlhausClient
import de.lemke.oneurl.data.DefaultVolleyQueue
import de.lemke.oneurl.data.GuardedDelivery
import de.lemke.oneurl.data.NetworkMonitor
import de.lemke.oneurl.data.PageTitleFetcher
import de.lemke.oneurl.data.ShortURLClient
import de.lemke.oneurl.data.UrlhausClient
import de.lemke.oneurl.data.VolleyQueue
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface NetworkModule {
    @Binds
    fun bindVolleyQueue(queue: DefaultVolleyQueue): VolleyQueue

    @Binds
    fun bindNetworkMonitor(monitor: DefaultNetworkMonitor): NetworkMonitor

    @Binds
    fun bindUrlhausClient(client: DefaultUrlhausClient): UrlhausClient

    @Binds
    fun bindPageTitleFetcher(fetcher: DefaultPageTitleFetcher): PageTitleFetcher

    @Binds
    fun bindShortURLClient(client: DefaultShortURLClient): ShortURLClient

    companion object {
        private const val NETWORK_THREADS = 4

        @Provides
        @Singleton
        fun provideRequestQueue(): RequestQueue =
            RequestQueue(
                NoCache(),
                BasicNetwork(HurlStack()),
                NETWORK_THREADS,
                GuardedDelivery(Handler(Looper.getMainLooper())),
            ).apply { start() }
    }
}
