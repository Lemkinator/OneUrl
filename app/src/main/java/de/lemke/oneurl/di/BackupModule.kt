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

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import de.lemke.commonutils.backup.BackupSectionLabel
import de.lemke.commonutils.backup.core.BackupApp
import de.lemke.commonutils.backup.core.BackupFileExtension
import de.lemke.commonutils.backup.core.BackupSection
import de.lemke.commonutils.backup.core.BackupTransaction
import de.lemke.commonutils.di.CommonUtilsBackupModule
import de.lemke.oneurl.BuildConfig
import de.lemke.oneurl.R
import de.lemke.oneurl.data.backup.RoomBackupTransaction
import de.lemke.oneurl.data.backup.UrlBackupSection

@Module(includes = [CommonUtilsBackupModule::class])
@InstallIn(SingletonComponent::class)
interface BackupModule {
    @Binds
    @IntoSet
    fun bindUrlSection(section: UrlBackupSection): BackupSection<*>

    @Binds
    fun bindBackupTransaction(transaction: RoomBackupTransaction): BackupTransaction

    companion object {
        @Provides
        @IntoSet
        fun provideUrlSectionLabel(): BackupSectionLabel = BackupSectionLabel(UrlBackupSection.ID, R.string.backup_section_urls)

        @Provides
        fun provideBackupApp(): BackupApp = BackupApp("oneurl", BuildConfig.VERSION_CODE.toLong(), BackupFileExtension(".oneurl"))
    }
}
