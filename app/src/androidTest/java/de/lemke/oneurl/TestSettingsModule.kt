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

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import de.lemke.commonutils.freshTestPreferences
import de.lemke.oneurl.data.UserSettings
import de.lemke.oneurl.di.ApplicationScope
import de.lemke.oneurl.di.SettingsProvideModule
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [SettingsProvideModule::class])
object TestSettingsModule {
    @Provides
    @Singleton
    fun provideTestUserSettings(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): UserSettings = UserSettings(freshTestPreferences(context), scope)
}
