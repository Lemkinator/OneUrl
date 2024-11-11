package de.lemke.oneurl.domain

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ActivityContext
import de.lemke.oneurl.R
import javax.inject.Inject

class OpenAppUseCase @Inject constructor(
    @ActivityContext private val context: Context,
) {
    operator fun invoke(packageName: String, tryLocalFirst: Boolean) {
        if (tryLocalFirst) openAppWithPackageName(packageName)
        else openAppWithPackageNameOnStore(packageName)
    }

    private fun openAppWithPackageName(packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        else openAppWithPackageNameOnStore(packageName)
    }

    private fun openAppWithPackageNameOnStore(packageName: String) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(context.getString(R.string.playstore_app_link) + packageName)
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (anfe: ActivityNotFoundException) {
            anfe.printStackTrace()
            intent.data = Uri.parse(context.getString(R.string.playstore_link) + packageName)
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}