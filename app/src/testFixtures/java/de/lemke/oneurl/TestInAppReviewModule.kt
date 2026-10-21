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
import de.lemke.commonutils.ui.utils.FakeInAppReview
import de.lemke.commonutils.ui.utils.InAppReview
import de.lemke.oneurl.di.InAppReviewModule
import javax.inject.Singleton

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [InAppReviewModule::class])
object TestInAppReviewModule {
    @Provides
    @Singleton
    fun provideFakeInAppReview(): FakeInAppReview = FakeInAppReview()

    @Provides
    fun provideInAppReview(fake: FakeInAppReview): InAppReview = fake
}
