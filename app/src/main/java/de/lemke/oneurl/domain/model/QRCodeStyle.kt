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
 * The look of a customized QR code: its pixel [size], colors, whether the foreground tints the anchors and the border,
 * whether the launcher [icon] sits in the center and whether a rounded frame surrounds it.
 */
data class QRCodeStyle(
    val size: Int,
    val foregroundColor: Int,
    val backgroundColor: Int,
    val tintAnchor: Boolean,
    val tintBorder: Boolean,
    val icon: Boolean,
    val roundedFrame: Boolean,
)
