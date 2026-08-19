package dev.enthusiastdev.netinspector.ui.screens.tools.history

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** `ACTION_CREATE_DOCUMENT` (via `ActivityResultContracts.CreateDocument`) only hands back the
 * destination [Uri] - writing the content through it is the caller's job, done off the main
 * thread since it goes through the returned [Uri]'s content provider. */
internal fun writeExport(
    context: Context,
    scope: CoroutineScope,
    uri: Uri,
    content: String,
) {
    scope.launch(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
    }
}
