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

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.domain.model.ShortURLProviderCompanion
import javax.inject.Qualifier

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class EnabledProviders

@Module
@InstallIn(SingletonComponent::class)
object ProviderModule {
    @Provides
    @EnabledProviders
    fun provideEnabledProviders(): List<ShortURLProvider> = ShortURLProviderCompanion.enabled
}
