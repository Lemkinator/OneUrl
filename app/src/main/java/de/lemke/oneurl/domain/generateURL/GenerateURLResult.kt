package de.lemke.oneurl.domain.generateURL

sealed class GenerateURLResult {
    data class Success(val shortURL: String) : GenerateURLResult()
    data class Failure(val error: GenerateURLError) : GenerateURLResult()
}
