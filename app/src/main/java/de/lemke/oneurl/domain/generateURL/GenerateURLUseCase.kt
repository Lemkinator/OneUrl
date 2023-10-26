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
import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.domain.model.ShortURLProvider.DAGD
import de.lemke.oneurl.domain.model.ShortURLProvider.ISGD
import de.lemke.oneurl.domain.model.ShortURLProvider.ONEPTCO
import de.lemke.oneurl.domain.model.ShortURLProvider.OWOVC
import de.lemke.oneurl.domain.model.ShortURLProvider.OWOVCGAY
import de.lemke.oneurl.domain.model.ShortURLProvider.OWOVCSKETCHY
import de.lemke.oneurl.domain.model.ShortURLProvider.OWOVCZWS
import de.lemke.oneurl.domain.model.ShortURLProvider.TINYURL
import de.lemke.oneurl.domain.model.ShortURLProvider.ULVIS
import de.lemke.oneurl.domain.model.ShortURLProvider.VGD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import javax.inject.Inject


class GenerateURLUseCase @Inject constructor(
    @ActivityContext private val context: Context,
    private val generateDAGD: GenerateDAGDUseCase,
    private val generateVGDISGD: GenerateVGDISGDUseCase,
    private val generateTINYURL: GenerateTINYURLUseCase,
    private val generateULVIS: GenerateULVISUseCase,
    private val generateONEPTCOUseCase: GenerateONEPTCOUseCase,
    private val generateOWOVCUseCase: GenerateOWOVCUseCase,
) {
    suspend operator fun invoke(
        provider: ShortURLProvider,
        longURL: String,
        alias: String? = null,
        successCallback: (shortURL: String) -> Unit = { },
        errorCallback: (message: String) -> Unit = { },
    ) = withContext(Dispatchers.Default) {
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
        generate(requestQueue, provider, longURL, alias, successCallback, errorCallback)
    }

    private fun generate(
        requestQueue: RequestQueue,
        provider: ShortURLProvider,
        longURL: String,
        alias: String?,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (message: String) -> Unit,
    ) {
        requestQueue.add(
            when (provider) {
                DAGD -> generateDAGD(requestQueue, provider, longURL, alias, successCallback, errorCallback)
                VGD, ISGD -> generateVGDISGD(provider, longURL, alias, successCallback, errorCallback)
                TINYURL -> generateTINYURL(provider, longURL, alias, successCallback, errorCallback)
                ULVIS -> generateULVIS(provider, longURL, alias, successCallback, errorCallback)
                ONEPTCO -> generateONEPTCOUseCase(provider, longURL, alias, successCallback, errorCallback)
                OWOVC, OWOVCZWS, OWOVCSKETCHY, OWOVCGAY -> generateOWOVCUseCase(provider, longURL, successCallback, errorCallback)
            }
        )
    }
}