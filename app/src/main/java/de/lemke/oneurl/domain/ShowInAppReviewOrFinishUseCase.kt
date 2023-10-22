package de.lemke.oneurl.domain

import android.app.Activity
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class ShowInAppReviewOrFinishUseCase @Inject constructor(
    private val getUserSettings: GetUserSettingsUseCase,
    private val updateUserSettings: UpdateUserSettingsUseCase,
) {
    suspend operator fun invoke(activity: Activity) {
        val tag = "ShowInAppReviewOrFinishUseCase"
        try {
            val lastInAppReviewRequest = getUserSettings().lastInAppReviewRequest
            val daysSinceLastRequest = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - lastInAppReviewRequest)
            if (daysSinceLastRequest < 14) {
                Log.d(tag, "In app review requested less than 14 days ago, skipping")
                activity.finishAfterTransition()
                return
            }
            updateUserSettings { it.copy(lastInAppReviewRequest = System.currentTimeMillis()) }
            val manager = ReviewManagerFactory.create(activity)
            //val manager = FakeReviewManager(activity)
            val request = manager.requestReviewFlow()
            request.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(tag, "Review task successful")
                    val reviewInfo = task.result
                    val flow = manager.launchReviewFlow(activity, reviewInfo)
                    flow.addOnCompleteListener {
                        Log.d(tag, "Review flow complete")
                        activity.finishAfterTransition()
                    }
                } else {
                    // There was some problem, log or handle the error code.
                    Log.e(tag, "Review task failed: ${task.exception?.message}")
                    activity.finishAfterTransition()
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error: ${e.message}")
            activity.finishAfterTransition()
        }
    }
}

