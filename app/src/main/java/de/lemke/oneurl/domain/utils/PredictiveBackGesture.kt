package de.lemke.oneurl.domain.utils

import android.os.Build
import android.view.View
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.animation.PathInterpolatorCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import de.lemke.oneurl.R
import kotlinx.coroutines.flow.MutableStateFlow
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

val GestureInterpolator = PathInterpolatorCompat.create(0f, 0f, 0f, 1f)

inline fun AppCompatActivity.setCustomAnimatedOnBackPressedLogic(
    animatedView: View,
    finishActivityEnabled: Boolean,
    crossinline onBackPressedLogic: () -> Unit = {}
) = setCustomAnimatedOnBackPressedLogic(animatedView, MutableStateFlow(finishActivityEnabled), onBackPressedLogic)

inline fun AppCompatActivity.setCustomAnimatedOnBackPressedLogic(
    animatedView: View,
    finishActivityEnabled: StateFlow<Boolean>? = null,
    crossinline onBackPressedLogic: () -> Unit = {}
) {
    val predictiveBackMargin = resources.getDimension(R.dimen.predictive_back_margin)
    var initialTouchY = -1f
    onBackPressedDispatcher.addCallback(
        this,
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (finishActivityEnabled?.value == false) {
                    onBackPressedLogic.invoke()
                } else {
                    finishAfterTransition()
                }
            }

            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                if (finishActivityEnabled?.value == false) return
                val progress = GestureInterpolator.getInterpolation(backEvent.progress)
                if (initialTouchY < 0f) {
                    initialTouchY = backEvent.touchY
                }
                val progressY = GestureInterpolator.getInterpolation(
                    (backEvent.touchY - initialTouchY) / animatedView.height
                )

                // See the motion spec about the calculations below.
                // https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back#motion-specs

                // Shift horizontally.
                val maxTranslationX = (animatedView.width / 20) - predictiveBackMargin
                animatedView.translationX = progress * maxTranslationX *
                        (if (backEvent.swipeEdge == BackEventCompat.EDGE_LEFT) 1 else -1)

                // Shift vertically.
                val maxTranslationY = (animatedView.height / 20) - predictiveBackMargin
                animatedView.translationY = progressY * maxTranslationY

                // Scale down from 100% to 90%.
                val scale = 1f - (0.1f * progress)
                animatedView.scaleX = scale
                animatedView.scaleY = scale
            }

            override fun handleOnBackCancelled() {
                initialTouchY = -1f
                animatedView.run {
                    translationX = 0f
                    translationY = 0f
                    scaleX = 1f
                    scaleY = 1f
                }
            }
        }
    )
}
