package de.docgerdsoft.pantrytracker.ui.scan

import de.docgerdsoft.pantrytracker.repository.ScanCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM coverage for [ScanUiState] and its [ScanUiState.Phase] hierarchy,
 * focused on the construction-time invariant (NotInInventory is Remove-mode
 * only) and the phase data carriers.
 */
class ScanUiStateTest {

    @Test
    fun default_isAddMode_idlePhase() {
        val state = ScanUiState()
        assertEquals(ScanMode.Add, state.mode)
        assertEquals(ScanUiState.Phase.Idle, state.phase)
    }

    @Test
    fun notInInventory_inRemoveMode_isAllowed() {
        val state = ScanUiState(
            mode = ScanMode.Remove,
            phase = ScanUiState.Phase.NotInInventory(barcode = "111"),
        )
        assertEquals(ScanMode.Remove, state.mode)
        assertTrue(state.phase is ScanUiState.Phase.NotInInventory)
    }

    @Test
    fun notInInventory_inAddMode_isRejected() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            ScanUiState(
                mode = ScanMode.Add,
                phase = ScanUiState.Phase.NotInInventory(barcode = "111"),
            )
        }
        assertTrue(ex.message!!.contains("NotInInventory"))
    }

    @Test
    fun otherPhases_areAllowedInAddMode() {
        // None of these trip the NotInInventory guard, so all must construct.
        ScanUiState(phase = ScanUiState.Phase.Loading(barcode = "5449000000996"))
        ScanUiState(phase = ScanUiState.Phase.ManualEntry(barcode = "999", pendingQuantity = 2))
        ScanUiState(phase = ScanUiState.Phase.Error(message = "Couldn't scan: boom"))
        ScanUiState(
            phase = ScanUiState.Phase.Preview(
                candidate = ScanCandidate.FromOff(
                    barcode = "5449000000996",
                    name = "Coke",
                    brand = "Coca-Cola",
                    imageUrl = null,
                ),
                pendingQuantity = 1,
            ),
        )
    }

    @Test
    fun phaseDataClasses_carryTheirFields() {
        assertEquals("123", (ScanUiState.Phase.Loading("123")).barcode)
        val manual = ScanUiState.Phase.ManualEntry(barcode = "456", pendingQuantity = 3)
        assertEquals("456", manual.barcode)
        assertEquals(3, manual.pendingQuantity)
        assertEquals("Couldn't scan: x", ScanUiState.Phase.Error("Couldn't scan: x").message)
        assertEquals("789", ScanUiState.Phase.NotInInventory("789").barcode)
    }

    @Test
    fun copy_preservesInvariant_whenSwitchingModeWithSafePhase() {
        val state = ScanUiState(mode = ScanMode.Add, phase = ScanUiState.Phase.Idle)
        val switched = state.copy(mode = ScanMode.Remove)
        assertEquals(ScanMode.Remove, switched.mode)
        assertEquals(ScanUiState.Phase.Idle, switched.phase)
    }
}
