package blbl.cat3399.core.io

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

data class CreateDocumentRequest(
    val mimeType: String,
    val fileName: String,
)

class CreateDocumentContract : ActivityResultContract<CreateDocumentRequest, Uri?>() {
    override fun createIntent(
        context: Context,
        input: CreateDocumentRequest,
    ): Intent {
        return Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.mimeType.ifBlank { "*/*" })
            .putExtra(Intent.EXTRA_TITLE, input.fileName)
    }

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): Uri? {
        if (resultCode != Activity.RESULT_OK) return null
        return intent?.data
    }
}
