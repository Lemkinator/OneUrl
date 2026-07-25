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

package de.lemke.oneurl.domain.generateURL

sealed class GenerateURLError {
    data class Unknown(val statusCode: Int? = null) : GenerateURLError()

    data class Custom(val statusCode: Int, val customMessage: String, val customTitle: String? = null) : GenerateURLError()

    data class ServiceTemporarilyUnavailable(val providerBaseURL: String) : GenerateURLError()

    data class BlacklistedURL(
        val message: String? = null,
        val urlhausLink: String? = null,
        val virustotalLink: String? = null,
    ) : GenerateURLError()

    data object NoInternet : GenerateURLError()

    data object RateLimitExceeded : GenerateURLError()

    data object DomainNotAllowed : GenerateURLError()

    data object AliasAlreadyExists : GenerateURLError()

    data object URLExistsWithDifferentAlias : GenerateURLError()

    data object InvalidURL : GenerateURLError()

    data object InvalidAlias : GenerateURLError()

    data object InvalidURLOrAlias : GenerateURLError()

    data object InternalServerError : GenerateURLError()

    data object ServiceOffline : GenerateURLError()
}
