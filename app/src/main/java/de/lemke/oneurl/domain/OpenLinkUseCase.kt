package de.lemke.oneurl.domain

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import dagger.hilt.android.qualifiers.ActivityContext
import de.lemke.oneurl.R
import javax.inject.Inject

class OpenLinkUseCase @Inject constructor(
    @ActivityContext private val context: Context,
) {
    operator fun invoke(link: String?) {
        try {
            if (link.isNullOrBlank()) {
                Log.e("OpenLinkUseCase", "link is null or blank")
                Toast.makeText(context, context.getString(R.string.error_cant_open_link), Toast.LENGTH_SHORT).show()
            } else context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
        } catch (e: ActivityNotFoundException) {
            e.printStackTrace()
            Toast.makeText(context, context.getString(R.string.no_browser_app_installed), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, context.getString(R.string.error_cant_open_link), Toast.LENGTH_SHORT).show()
        }
    }

}