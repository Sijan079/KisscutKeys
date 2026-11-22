package app.kisscut.keys

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kisscut.keys.share.sharedImageUris
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class SharedImagesTest {
    private val a = Uri.parse("content://media/external/images/media/1")
    private val b = Uri.parse("content://media/external/images/media/2")

    @Test
    fun sendReadsTheStream() {
        val intent = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, a)
        assertEquals(listOf(a), intent.sharedImageUris())
    }

    @Test
    fun sendMultipleKeepsOrderAndDropsRepeats() {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/*")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(b, a, b))
        intent.clipData = ClipData.newRawUri(null, b).apply { addItem(ClipData.Item(a)) }
        assertEquals(listOf(b, a), intent.sharedImageUris())
    }

    @Test
    fun fileUrisAndOtherTypesAreIgnored() {
        val file = Intent(Intent.ACTION_SEND).setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, Uri.parse("file:///sdcard/x.png"))
        assertEquals(emptyList(), file.sharedImageUris())

        val text = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, a)
        assertEquals(emptyList(), text.sharedImageUris())

        assertEquals(emptyList(), Intent(Intent.ACTION_VIEW).setData(a).sharedImageUris())
    }
}
