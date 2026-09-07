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

/** HTTP status codes returned by URL-shortener provider APIs, used to map responses to a [GenerateURLError]. */
object HttpStatusCode {
    const val OK = 200
    const val ALREADY_REPORTED = 208
    const val BAD_REQUEST = 400
    const val FORBIDDEN = 403
    const val NOT_FOUND = 404
    const val LOCKED = 423
    const val UNPROCESSABLE_ENTITY = 422
    const val TOO_MANY_REQUESTS = 429

    // Nonstandard (nginx): connection closed with no response.
    const val NO_RESPONSE = 444
    const val INTERNAL_SERVER_ERROR = 500
    const val SERVICE_UNAVAILABLE = 503
}
