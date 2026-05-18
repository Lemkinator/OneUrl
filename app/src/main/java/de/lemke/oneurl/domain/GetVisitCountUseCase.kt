package de.lemke.oneurl.domain

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lemke.oneurl.domain.model.URL
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

class GetVisitCountUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend operator fun invoke(url: URL): Int? = suspendCancellableCoroutine { cont ->
        url.shortURLProvider.getURLClickCount(context, url) { count ->
            if (cont.isActive) cont.resume(count)
        }
    }
}
