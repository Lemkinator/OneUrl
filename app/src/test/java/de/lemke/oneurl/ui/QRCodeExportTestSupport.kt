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

import android.app.Activity
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import de.lemke.oneurl.data.DefaultQRCodeExporter
import de.lemke.oneurl.data.QRCodeExporter
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.CoroutineDispatcher
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

internal val PNG_SIGNATURE = listOf<Byte>(-119, 80, 78, 71, 13, 10, 26, 10)

/** The app's QR code exporter with its IO work on [ioDispatcher]. */
internal fun qrCodeExporterOn(ioDispatcher: CoroutineDispatcher): QRCodeExporter =
    DefaultQRCodeExporter(ApplicationProvider.getApplicationContext(), ioDispatcher)

/** An empty file, as the document picker creates it; a file:// URI of it is writable under Robolectric. */
internal fun createPickedDocument(): File =
    File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "export.png").apply { createNewFile() }

/** Delivers [resultCode] and [data] to the document picker that this activity started last. */
internal fun Activity.receiveDocumentPickerResult(
    resultCode: Int,
    data: Intent?,
) {
    val shadowActivity = shadowOf(this)
    shadowActivity.receiveResult(shadowActivity.nextStartedActivityForResult.intent, resultCode, data)
}

/** Registers a [ReadOnlyDocumentProvider] whose document is a new picked document. */
internal fun readOnlyDocumentProvider(): ReadOnlyDocumentProvider =
    Robolectric
        .buildContentProvider(ReadOnlyDocumentProvider::class.java)
        .create(DOCUMENTS_AUTHORITY)
        .get()
        .apply { document = createPickedDocument() }

private const val DOCUMENTS_AUTHORITY = "de.lemke.oneurl.test.documents"

// The hidden DocumentsContract.METHOD_DELETE_DOCUMENT that deleteDocument sends to the provider.
private const val METHOD_DELETE_DOCUMENT = "android:deleteDocument"

/** A document provider that refuses every write to its one document and deletes it on request. */
internal class ReadOnlyDocumentProvider : ContentProvider() {
    lateinit var document: File
    val uri: Uri = Uri.parse("content://$DOCUMENTS_AUTHORITY/document/1")

    override fun onCreate() = true

    override fun call(
        method: String,
        arg: String?,
        extras: Bundle?,
    ): Bundle? {
        if (method == METHOD_DELETE_DOCUMENT) document.delete()
        return null
    }

    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("$uri is read-only")
        return ParcelFileDescriptor.open(document, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

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
