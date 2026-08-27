package dev.enthusiastdev.netinspector.debug

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** ideas.md #21/#22 - the literal shared infrastructure between crash-report
 * export and debug-bundle export: both hand a local file to the standard Android share sheet
 * so the user picks the destination themselves (email, Drive, chat app, ...). Nothing here
 * ever transmits anything on its own. */
object ShareFileLauncher {
    fun share(
        context: Context,
        file: File,
        mimeType: String,
        chooserTitle: String,
    ) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val sendIntent =
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        // Launched from a ViewModel-held application Context, not an Activity, so the chooser
        // needs its own task.
        val chooser = Intent.createChooser(sendIntent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
