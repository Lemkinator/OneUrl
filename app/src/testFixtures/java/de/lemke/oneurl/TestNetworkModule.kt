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

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import de.lemke.oneurl.data.FakeNetworkMonitor
import de.lemke.oneurl.data.FakePageTitleFetcher
import de.lemke.oneurl.data.FakeShortURLClient
import de.lemke.oneurl.data.FakeUrlhausClient
import de.lemke.oneurl.data.NetworkMonitor
import de.lemke.oneurl.data.PageTitleFetcher
import de.lemke.oneurl.data.ShortURLClient
import de.lemke.oneurl.data.UrlhausClient
import de.lemke.oneurl.di.NetworkModule
import javax.inject.Singleton

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [NetworkModule::class])
object TestNetworkModule {
    @Provides
    @Singleton
    fun provideFakeNetworkMonitor(): FakeNetworkMonitor = FakeNetworkMonitor()

    @Provides
    fun provideNetworkMonitor(fake: FakeNetworkMonitor): NetworkMonitor = fake

    @Provides
    @Singleton
    fun provideFakeUrlhausClient(): FakeUrlhausClient = FakeUrlhausClient()

    @Provides
    fun provideUrlhausClient(fake: FakeUrlhausClient): UrlhausClient = fake

    @Provides
    @Singleton
    fun provideFakePageTitleFetcher(): FakePageTitleFetcher = FakePageTitleFetcher()

    @Provides
    fun providePageTitleFetcher(fake: FakePageTitleFetcher): PageTitleFetcher = fake

    @Provides
    @Singleton
    fun provideFakeShortURLClient(): FakeShortURLClient = FakeShortURLClient()

    @Provides
    fun provideShortURLClient(fake: FakeShortURLClient): ShortURLClient = fake
}
