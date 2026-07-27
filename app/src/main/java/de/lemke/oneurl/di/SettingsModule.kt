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

import android.content.Context
import androidx.preference.PreferenceManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.oneurl.data.UserSettings
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

@Module
@InstallIn(SingletonComponent::class)
object SettingsProvideModule {
    @Provides
    @Singleton
    fun provideUserSettings(
        @ApplicationContext c: Context,
        @ApplicationScope scope: CoroutineScope,
    ): UserSettings = UserSettings(PreferenceManager.getDefaultSharedPreferences(c), scope)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsBindModule {
    @Binds
    abstract fun bindSettingsRepository(u: UserSettings): SettingsRepository
}
