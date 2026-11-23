package app.kisscut.keys.setup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class KeyboardSetupViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeStatus : KeyboardStatusSource {
        val value = MutableStateFlow(KeyboardStatus(enabled = false, selected = false))
        var refreshes = 0
        override fun observe() = value
        override fun refresh() {
            refreshes++
        }
    }

    private class FakeStore : SetupStore {
        override val dismissed = MutableStateFlow(false)
        override suspend fun dismiss() {
            dismissed.value = true
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun stepsFollowTheRealState() {
        val off = setupState(KeyboardStatus(enabled = false, selected = false), hasStickers = false)
        assertEquals(listOf(StepState.CURRENT, StepState.LATER, StepState.LATER), off.steps)
        assertEquals(SetupAction.OPEN_SETTINGS, off.action)

        val on = setupState(KeyboardStatus(enabled = true, selected = false), hasStickers = false)
        assertEquals(listOf(StepState.DONE, StepState.CURRENT, StepState.LATER), on.steps)
        assertEquals(SetupAction.PICK_KEYBOARD, on.action)

        val chosen = setupState(KeyboardStatus(enabled = true, selected = true), hasStickers = false)
        assertEquals(SetupAction.ADD_STICKERS, chosen.action)

        val all = setupState(KeyboardStatus(enabled = true, selected = true), hasStickers = true)
        assertEquals(listOf(StepState.DONE, StepState.DONE, StepState.DONE), all.steps)
        assertTrue(all.allDone)
    }

    @Test
    fun stickersCanBeAddedBeforeTheKeyboardIsOn() {
        val s = setupState(KeyboardStatus(enabled = false, selected = false), hasStickers = true)
        assertEquals(listOf(StepState.CURRENT, StepState.LATER, StepState.DONE), s.steps)
        assertEquals(SetupAction.OPEN_SETTINGS, s.action)
    }

    @Test
    fun selectedButDisabledIsNotChosen() {
        // Cannot happen in practice, but "chosen" needs "enabled" first.
        val s = setupState(KeyboardStatus(enabled = false, selected = true), hasStickers = false)
        assertEquals(StepState.LATER, s.steps[1])
    }

    @Test
    fun stateUpdatesWhenTheUserComesBack() = runTest(dispatcher) {
        val status = FakeStatus()
        val count = MutableStateFlow(0)
        val vm = KeyboardSetupViewModel(status, count, FakeStore())
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        assertEquals(SetupAction.OPEN_SETTINGS, vm.state.value.action)

        status.value.value = KeyboardStatus(enabled = true, selected = true)
        count.value = 3
        advanceUntilIdle()
        assertTrue(vm.state.value.allDone)

        vm.refresh()
        assertEquals(1, status.refreshes)
    }

    @Test
    fun dismissIsRememberedThenLeaves() = runTest(dispatcher) {
        val store = FakeStore()
        val vm = KeyboardSetupViewModel(FakeStatus(), MutableStateFlow(0), store)
        var left = false
        vm.dismiss { left = true }
        assertFalse(left)
        advanceUntilIdle()
        assertTrue(store.dismissed.value)
        assertTrue(left)
    }
}
