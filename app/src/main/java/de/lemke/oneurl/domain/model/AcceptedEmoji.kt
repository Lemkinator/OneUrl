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

package de.lemke.oneurl.domain.model

/**
 * The emoji the spoo.me server takes as an alias: a single code point with the Unicode 16.0 Emoji_Presentation property
 * (emoji-data.txt), without the Emoji_Modifier skin tones and the regional indicators. A fixed table gives the same answer on
 * every Android version, whose ICU data knows different Unicode versions.
 */
internal object AcceptedEmoji {
    private const val HEX_RADIX = 16
    private const val RANGE_SEPARATOR = ".."

    // Hexadecimal code points and ranges in the notation of emoji-data.txt.
    private const val TABLE = """
        231A..231B 23E9..23EC 23F0 23F3 25FD..25FE 2614..2615 2648..2653 267F 2693 26A1 26AA..26AB 26BD..26BE 26C4..26C5 26CE
        26D4 26EA 26F2..26F3 26F5 26FA 26FD 2705 270A..270B 2728 274C 274E 2753..2755 2757 2795..2797 27B0 27BF 2B1B..2B1C 2B50
        2B55 1F004 1F0CF 1F18E 1F191..1F19A 1F201 1F21A 1F22F 1F232..1F236 1F238..1F23A 1F250..1F251 1F300..1F320 1F32D..1F335
        1F337..1F37C 1F37E..1F393 1F3A0..1F3CA 1F3CF..1F3D3 1F3E0..1F3F0 1F3F4 1F3F8..1F3FA 1F400..1F43E 1F440 1F442..1F4FC
        1F4FF..1F53D 1F54B..1F54E 1F550..1F567 1F57A 1F595..1F596 1F5A4 1F5FB..1F64F 1F680..1F6C5 1F6CC 1F6D0..1F6D2 1F6D5..1F6D7
        1F6DC..1F6DF 1F6EB..1F6EC 1F6F4..1F6FC 1F7E0..1F7EB 1F7F0 1F90C..1F93A 1F93C..1F945 1F947..1F9FF 1FA70..1FA7C
        1FA80..1FA89 1FA8F..1FAC6 1FACE..1FADC 1FADF..1FAE9 1FAF0..1FAF8
    """

    private val whitespace = Regex("\\s+")

    private val codePointRanges: List<IntRange> =
        TABLE.trim().split(whitespace).map { entry ->
            val bounds = entry.split(RANGE_SEPARATOR).map { it.toInt(HEX_RADIX) }
            bounds.first()..bounds.last()
        }

    fun containsAll(alias: String): Boolean = alias.codePoints().allMatch { codePoint -> codePointRanges.any { codePoint in it } }
}
