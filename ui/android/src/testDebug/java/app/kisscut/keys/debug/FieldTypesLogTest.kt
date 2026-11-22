package app.kisscut.keys.debug

import org.junit.Test
import kotlin.test.assertEquals

class FieldTypesLogTest {
    @Test
    fun listsTheAppAndEveryAcceptedType() {
        assertEquals(
            "org.telegram.messenger: image/gif, image/*, image/png",
            FieldTypesLog.line("org.telegram.messenger", arrayOf("image/gif", "image/*", "image/png")),
        )
    }

    @Test
    fun saysWhenAFieldAcceptsNoContent() {
        assertEquals("com.example.app: no content types", FieldTypesLog.line("com.example.app", emptyArray()))
    }

    @Test
    fun handlesAMissingPackageName() {
        assertEquals("unknown app: image/png", FieldTypesLog.line(null, arrayOf("image/png")))
    }
}
