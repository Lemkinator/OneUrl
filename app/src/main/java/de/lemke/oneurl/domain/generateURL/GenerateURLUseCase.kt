package de.lemke.oneurl.domain.generateURL


import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.android.volley.RequestQueue
import com.android.volley.toolbox.BasicNetwork
import com.android.volley.toolbox.DiskBasedCache
import com.android.volley.toolbox.HurlStack
import dagger.hilt.android.qualifiers.ActivityContext
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.GetURLUseCase
import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.domain.model.ShortURLProvider.DAGD
import de.lemke.oneurl.domain.model.ShortURLProvider.ISGD
import de.lemke.oneurl.domain.model.ShortURLProvider.TINYURL
import de.lemke.oneurl.domain.model.ShortURLProvider.VGD
import de.lemke.oneurl.domain.model.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import javax.inject.Inject


class GenerateURLUseCase @Inject constructor(
    @ActivityContext private val context: Context,
    private val getURL: GetURLUseCase,
    private val generateDAGD: GenerateDAGDUseCase,
    private val generateVGDISGD: GenerateVGDISGDUseCase,
    private val generateTINYURL: GenerateTINYURLUseCase,
) {
    suspend operator fun invoke(
        provider: ShortURLProvider,
        longURL: String,
        alias: String? = null,
        favorite: Boolean,
        description: String,
        successCallback: (url: URL) -> Unit = { },
        errorCallback: (message: String) -> Unit = { },
        alreadyShortenedCallback: (url: URL) -> Unit = { },
    ) = withContext(Dispatchers.Default) {
        val existingURL = getURL(provider, addHTTPSIfMissing(longURL))
        if (existingURL.isNotEmpty()) {
            if (alias.isNullOrBlank()) {
                alreadyShortenedCallback(existingURL.first())
                return@withContext
            }
            for (url in existingURL) {
                if (url.shortURL == provider.baseURL + alias) {
                    alreadyShortenedCallback(url)
                    return@withContext
                }
            }
        }
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        if (capabilities == null || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
            !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        ) {
            errorCallback(context.getString(R.string.no_internet))
            return@withContext
        }
        val cookieManager = CookieManager()
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL)
        CookieHandler.setDefault(cookieManager)
        // Instantiate the cache
        val cache = DiskBasedCache(context.cacheDir, 1024 * 1024) // 1MB cap
        // Set up the network to use HttpURLConnection as the HTTP client.
        val network = BasicNetwork(HurlStack())
        // Instantiate the RequestQueue with the cache and network. Start the queue.
        val requestQueue = RequestQueue(cache, network).apply {
            start()
        }
        generate(provider, addHTTPSIfMissing(longURL), alias, favorite, description, requestQueue, successCallback, errorCallback)
    }

    private fun addHTTPSIfMissing(url: String): String =
        if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"

    private fun generate(
        provider: ShortURLProvider,
        longURL: String,
        alias: String?,
        favorite: Boolean,
        description: String,
        requestQueue: RequestQueue,
        successCallback: (url: URL) -> Unit,
        errorCallback: (message: String) -> Unit
    ) {
        requestQueue.add(
            when (provider) {
                DAGD -> generateDAGD(requestQueue, provider, longURL, alias, favorite, description, successCallback, errorCallback)
                VGD, ISGD -> generateVGDISGD(provider, longURL, alias, errorCallback, favorite, description, successCallback)
                TINYURL -> generateTINYURL(provider, longURL, alias, favorite, description, successCallback, errorCallback)
            }
        )
    }
}