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

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager.GET_PROVIDERS
import android.content.pm.PackageManager.PackageInfoFlags
import android.database.Cursor
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import org.robolectric.Robolectric

private fun Context.fileProviderAuthority(): String =
    packageManager
        .getPackageInfo(packageName, PackageInfoFlags.of(GET_PROVIDERS.toLong()))
        .providers
        .orEmpty()
        .single { it.name == FileProvider::class.java.name }
        .authority

internal fun Context.qrCodeContentUri(fileName: String): Uri = "content://${fileProviderAuthority()}/qrCodes/$fileName".toUri()

// FileProvider caches its path roots per authority in the static sCache map, which outlives the cache
// dir of the Robolectric test that filled it. The published ShadowFileProvider (common-utils 1.2.7)
// resolves roots through sCache, so a strategy cached by an earlier test breaks later getUriForFile calls.
internal fun resetFileProviderCache() {
    val sCache = FileProvider::class.java.getDeclaredField("sCache")
    sCache.isAccessible = true
    (sCache.get(null) as MutableMap<*, *>).clear()
}

// ClipData.newUri asks the provider for the MIME type. The stock FileProvider maps the URI back to a
// File with '/'-only root matching and throws SecurityException on Windows; ShadowFileProvider only
// shadows getUriForFile.
internal fun Context.registerPngTypeProvider() {
    Robolectric.setupContentProvider(PngTypeProvider::class.java, fileProviderAuthority())
}

internal class PngTypeProvider : ContentProvider() {
    override fun onCreate() = true

    override fun getType(uri: Uri) = "image/png"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = 0
}
