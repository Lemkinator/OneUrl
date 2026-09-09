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
import de.lemke.oneurl.di.EnabledProviders
import de.lemke.oneurl.di.ProviderModule
import de.lemke.oneurl.domain.model.Dagd
import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.domain.model.Spoome
import de.lemke.oneurl.domain.model.VgdIsgd
import de.lemke.oneurl.domain.model.Zwsim

// A fixed, small provider list so screenshot tests stay stable when ShortURLProviderCompanion's
// real provider list grows or shrinks.
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [ProviderModule::class])
object TestProviderModule {
    @Provides
    @EnabledProviders
    fun provideTestEnabledProviders(): List<ShortURLProvider> = listOf(Dagd, VgdIsgd.Isgd, VgdIsgd.Vgd, Zwsim, Spoome.Default, Spoome.Emoji)
}
