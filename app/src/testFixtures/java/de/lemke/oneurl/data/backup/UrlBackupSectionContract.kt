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

package de.lemke.oneurl.data.backup

import de.lemke.commonutils.backup.BackupSectionContract
import de.lemke.commonutils.backup.ClashCase
import de.lemke.commonutils.backup.SectionFixture

/** The samples every run of the common-utils [BackupSectionContract] over [UrlBackupSection] shares. */
abstract class UrlBackupSectionContract : BackupSectionContract<UrlBackupEntry>() {
    override fun sampleEntries(): List<UrlBackupEntry> =
        listOf(entry("https://da.gd/a"), entry("https://da.gd/b", favorite = true, title = ""))

    override fun fixtures(): Map<Int, SectionFixture<UrlBackupEntry>> =
        mapOf(
            1 to
                SectionFixture(
                    """
                    {"entries":[
                    {"shortURL":"https://da.gd/abc","longURL":"https://example.com/a","shortURLProvider":"da.gd","favorite":true,"title":"Example A","description":"first","added":"2026-10-27T14:30:00+01:00"},
                    {"shortURL":"https://tinyurl.com/xyz","longURL":"https://example.com/b","shortURLProvider":"tinyurl.com","favorite":false,"title":"","description":"","added":"2026-10-27T09:00:00Z"}
                    ]}
                    """.trimIndent(),
                    listOf(
                        UrlBackupEntry(
                            shortURL = "https://da.gd/abc",
                            longURL = "https://example.com/a",
                            shortURLProvider = "da.gd",
                            favorite = true,
                            title = "Example A",
                            description = "first",
                            added = "2026-10-27T14:30:00+01:00",
                        ),
                        UrlBackupEntry(
                            shortURL = "https://tinyurl.com/xyz",
                            longURL = "https://example.com/b",
                            shortURLProvider = "tinyurl.com",
                            favorite = false,
                            title = "",
                            description = "",
                            added = "2026-10-27T10:00:00+01:00",
                        ),
                    ),
                ),
        )

    override fun brokenEntryJson(): String = """{"shortURL":"https://da.gd/broken"}"""

    override fun clashCases(): List<ClashCase<UrlBackupEntry>> =
        listOf(
            ClashCase(
                "favorite from file only is favorite",
                entry("https://da.gd/a", favorite = false),
                entry("https://da.gd/a", favorite = true),
                entry("https://da.gd/a", favorite = true),
            ),
            ClashCase(
                "favorite on phone only is favorite",
                entry("https://da.gd/a", favorite = true),
                entry("https://da.gd/a", favorite = false),
                entry("https://da.gd/a", favorite = true),
            ),
            ClashCase(
                "title and description stay from the phone",
                entry("https://da.gd/a", title = "phone", description = "phone note"),
                entry("https://da.gd/a", title = "file", description = "file note"),
                entry("https://da.gd/a", title = "phone", description = "phone note"),
                symmetric = false,
            ),
        )

    protected fun entry(
        shortURL: String,
        favorite: Boolean = false,
        title: String = "title",
        description: String = "description",
        added: String = "2026-10-27T14:30:00+01:00",
    ) = UrlBackupEntry(shortURL, "https://example.com", "da.gd", favorite, title, description, added)
}
