package de.lemke.oneurl.domain.utils

import android.os.Build
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Convenience method to implement custom onBackPressed logic with framework predictive gesture api (added in api33)
 * when using unsupported AppCompat version (i.e. < v1.6-alpha05)
 *
 * @param triggerStateFlow - (optional) Boolean StateFlow to trigger enabling (true) and disabling (false)
 * custom [onBackPressedLogic] on either framework onBackInvokedDispatcher(>= api 33) or onBackPressedDispatcher(< api 33).
 * Set none to keep it enabled.
 *
 * @param onBackPressedLogic - lambda to be invoked  for the custom onBackPressed logic
 *
 * Note: android:enableOnBackInvokedCallback="true" must be set in Manifest
 */
inline fun AppCompatActivity.setCustomOnBackPressedLogic(
    triggerStateFlow: StateFlow<Boolean>? = null,
    crossinline onBackPressedLogic: () -> Unit
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val onBackInvokedCallback = OnBackInvokedCallback {
            onBackPressedLogic.invoke()
        }
        onBackInvokedDispatcher.registerOnBackInvokedCallback(PRIORITY_DEFAULT, onBackInvokedCallback)
        if (triggerStateFlow != null) {
            lifecycleScope.launch {
                triggerStateFlow
                    .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                    .collectLatest { register ->
                        if (register) {
                            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                                PRIORITY_DEFAULT,
                                onBackInvokedCallback
                            )
                        } else {
                            onBackInvokedDispatcher.unregisterOnBackInvokedCallback(
                                onBackInvokedCallback
                            )
                        }
                    }
            }
        }
    } else {
        val onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onBackPressedLogic.invoke()
            }
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
        if (triggerStateFlow != null) {
            lifecycleScope.launch {
                triggerStateFlow
                    .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                    .collectLatest { enable ->
                        onBackPressedCallback.isEnabled = enable
                    }
            }
        }
    }
}

inline fun Fragment.setCustomOnBackPressedLogic(
    triggerStateFlow: StateFlow<Boolean>? = null,
    crossinline onBackPressedLogic: () -> Unit
) {
    (this.requireActivity() as AppCompatActivity).setCustomOnBackPressedLogic(triggerStateFlow, onBackPressedLogic)
}
