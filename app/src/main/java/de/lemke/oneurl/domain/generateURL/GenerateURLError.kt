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
