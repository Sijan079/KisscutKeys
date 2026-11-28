package app.kisscut.keys.licenses

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LicensesViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun opensALicenseAndBackReturnsToTheList() = runTest(dispatcher) {
        val vm = LicensesViewModel { file -> "text of $file" }
        val words = BUNDLED_LICENSES.first()
        vm.open(words)
        assertEquals(words, vm.state.value.open)
        assertNull(vm.state.value.text)
        advanceUntilIdle()
        assertEquals("text of ${words.file}", vm.state.value.text)

        assertTrue(vm.back())
        assertNull(vm.state.value.open)
        assertFalse(vm.back(), "on the list, Back leaves the screen")
    }

    @Test
    fun aSlowLoadNeverShowsUnderAnotherLicense() = runTest(dispatcher) {
        val vm = LicensesViewModel { file -> "text of $file" }
        vm.open(BUNDLED_LICENSES[0])
        vm.open(BUNDLED_LICENSES[1])
        advanceUntilIdle()
        assertEquals("text of ${BUNDLED_LICENSES[1].file}", vm.state.value.text)
    }

    @Test
    fun everyBundledLicenseFileShipsInTheAssets() {
        val dir = File("src/main/assets/licenses")
        for (license in BUNDLED_LICENSES) {
            val file = File(dir, license.file)
            assertTrue(file.isFile && file.length() > 1000, "missing or empty: $file")
        }
        assertTrue("Apache License" in File(dir, "Apache-2.0-AOSP-LatinIME.txt").readText())
        assertTrue("Android Open Source Project" in File(dir, "Apache-2.0-AOSP-LatinIME.txt").readText())
        val ngram = File(dir, "CC-BY-3.0-Google-Books-Ngram.txt").readText()
        assertTrue("Google Books Ngram" in ngram && "Creative Commons Attribution 3.0 Unported" in ngram)
    }
}
