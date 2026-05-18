package de.docgerdsoft.pantrytracker.ui.scan

import app.cash.turbine.test
import de.docgerdsoft.pantrytracker.data.local.Product
import de.docgerdsoft.pantrytracker.repository.ProductRepository
import de.docgerdsoft.pantrytracker.repository.ScanCandidate
import de.docgerdsoft.pantrytracker.util.JulLogCapture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
// Test classes naturally accumulate cases as the ViewModel surface grows; this
// file groups them by feature area with header comments so they remain readable
// despite the length. Splitting into per-feature classes would force the
// FakeProductRepository test double to move to a shared file, which trades one
// kind of complexity (one large file) for another (cross-file coupling).
@Suppress("LargeClass")
class ScanViewModelTest {

    private lateinit var fake: FakeProductRepository
    private lateinit var vm: ScanViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fake = FakeProductRepository()
        vm = ScanViewModel(fake)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialPhase_isIdle() = runTest {
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(ScanUiState.Phase.Idle, state.phase)
        }
    }

    @Test
    fun onBarcodeDecoded_knownBarcode_movesToPreviewWithDefaultQuantityOne() = runTest {
        val now = Clock.System.now()
        val product = Product(id = 1, barcode = "5449000000996", name = "Coca-Cola 0.5L",
            quantity = 0, createdAt = now, updatedAt = now)
        fake.seed(product)

        vm.uiState.test {
            awaitItem() // initial Idle
            vm.onBarcodeDecoded("5449000000996")
            awaitItem() // Loading
            val state = awaitItem()
            val preview = state.phase as ScanUiState.Phase.Preview
            assertEquals("Coca-Cola 0.5L", preview.candidate.name)
            assertEquals(1, preview.pendingQuantity)
        }
    }

    @Test
    fun onBarcodeDecoded_localMissAndOffMiss_movesToManualEntry() = runTest {
        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("0000000000000")
            awaitItem() // Loading
            val state = awaitItem()
            val manual = state.phase as ScanUiState.Phase.ManualEntry
            assertEquals("0000000000000", manual.barcode)
        }
    }

    @Test
    fun onBarcodeDecoded_ignoredWhenAlreadyInPreviewPhase() = runTest {
        val now = Clock.System.now()
        fake.seed(Product(id = 1, barcode = "5449000000996", name = "Coca-Cola",
            quantity = 0, createdAt = now, updatedAt = now))

        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("5449000000996")
            awaitItem() // Loading
            awaitItem() // Preview
            vm.onBarcodeDecoded("5449000000996") // duplicate scan while sheet open
            expectNoEvents()
        }
    }

    @Test
    fun setQuantity_belowOne_clampsToOne() = runTest {
        val now = Clock.System.now()
        fake.seed(Product(id = 1, barcode = "x", name = "P", quantity = 0,
            createdAt = now, updatedAt = now))

        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("x")
            awaitItem() // Loading
            awaitItem() // Preview pendingQuantity=1
            vm.setQuantity(0)
            expectNoEvents() // already at 1, no new emission
            vm.setQuantity(-5)
            expectNoEvents()
            assertEquals(1, (vm.uiState.value.phase as ScanUiState.Phase.Preview).pendingQuantity)
        }
    }

    @Test
    fun setQuantity_positiveValue_updatesPreview() = runTest {
        val now = Clock.System.now()
        fake.seed(Product(id = 1, barcode = "x", name = "P", quantity = 0,
            createdAt = now, updatedAt = now))

        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("x")
            awaitItem() // Loading
            awaitItem()
            vm.setQuantity(6)
            val state = awaitItem()
            assertEquals(6, (state.phase as ScanUiState.Phase.Preview).pendingQuantity)
        }
    }

    @Test
    fun confirm_appliesDeltaAndReturnsToIdle() = runTest {
        val now = Clock.System.now()
        fake.seed(Product(id = 1, barcode = "x", name = "P", quantity = 0,
            createdAt = now, updatedAt = now))

        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("x")
            awaitItem() // Loading
            awaitItem()
            vm.setQuantity(3)
            awaitItem()
            vm.confirm()
            val state = awaitItem()
            assertEquals(ScanUiState.Phase.Idle, state.phase)
        }
        assertEquals(1L to 3, fake.lastDelta)
    }

    @Test
    fun cancelPreview_returnsToIdleWithoutCallingRepository() = runTest {
        val now = Clock.System.now()
        fake.seed(Product(id = 1, barcode = "x", name = "P", quantity = 0,
            createdAt = now, updatedAt = now))

        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("x")
            awaitItem() // Loading
            awaitItem()
            vm.dismissPreview()
            assertEquals(ScanUiState.Phase.Idle, awaitItem().phase)
        }
        assertNull(fake.lastDelta)
    }

    @Test
    fun dismissManualEntry_returnsToIdle() = runTest {
        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("nope")
            awaitItem() // Loading
            assertTrue(awaitItem().phase is ScanUiState.Phase.ManualEntry)
            vm.dismissPreview()
            assertEquals(ScanUiState.Phase.Idle, awaitItem().phase)
        }
    }

    @Test
    fun confirm_fromIdle_isNoOp() = runTest {
        vm.uiState.test {
            awaitItem() // Idle
            vm.confirm()
            expectNoEvents()
        }
        assertNull(fake.lastDelta)
    }

    @Test
    fun setQuantity_fromIdle_isNoOp() = runTest {
        vm.uiState.test {
            assertEquals(ScanUiState.Phase.Idle, awaitItem().phase)
            vm.setQuantity(5)
            expectNoEvents()
            assertEquals(ScanUiState.Phase.Idle, vm.uiState.value.phase)
        }
    }

    @Test
    fun onBarcodeDecoded_ignoredWhenAlreadyInManualEntryPhase() = runTest {
        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("nope")
            awaitItem() // Loading
            assertTrue(awaitItem().phase is ScanUiState.Phase.ManualEntry)
            vm.onBarcodeDecoded("nope") // duplicate while sheet is open
            expectNoEvents()
        }
    }

    @Test
    fun confirm_passesCorrectProductId() = runTest {
        val now = Clock.System.now()
        fake.seed(Product(id = 42L, barcode = "x", name = "P", quantity = 0,
            createdAt = now, updatedAt = now))

        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("x")
            awaitItem() // Loading
            awaitItem()
            vm.setQuantity(2)
            awaitItem()
            vm.confirm()
            awaitItem()
        }
        assertEquals(42L to 2, fake.lastDelta)
    }

    @Test
    fun onBarcodeDecoded_blankBarcode_isIgnored() = runTest {
        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("")
            expectNoEvents()
            vm.onBarcodeDecoded("   ")
            expectNoEvents()
        }
    }

    // ---- New tests for T4 ----

    @Test
    fun onBarcodeDecoded_localMiss_offHit_transitionsThroughLoadingToPreview() = runTest {
        // OFF hit is expressed as a ScanCandidate.FromOff (id=0 sentinel gone)
        fake.lookupResponses["222"] = ScanCandidate.FromOff(
            barcode = "222", name = "OFF Result", brand = null, imageUrl = null,
        )
        vm.uiState.test {
            awaitItem() // initial Idle
            vm.onBarcodeDecoded("222")
            val loading = awaitItem().phase
            assertTrue("expected Loading, was $loading", loading is ScanUiState.Phase.Loading)
            assertEquals("222", (loading as ScanUiState.Phase.Loading).barcode)
            val preview = awaitItem().phase
            assertTrue(preview is ScanUiState.Phase.Preview)
            assertEquals("OFF Result", (preview as ScanUiState.Phase.Preview).candidate.name)
        }
    }

    @Test
    fun onBarcodeDecoded_localHit_transitionsThroughLoadingToPreview() = runTest {
        val now = Clock.System.now()
        fake.seed(Product(id = 1, barcode = "111", name = "Local Coke",
            quantity = 3, createdAt = now, updatedAt = now))
        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("111")
            // Loading sheet flashes even for local hits — caller decision, keeps the path
            // identical regardless of where the data comes from.
            awaitItem() // Loading
            val preview = awaitItem().phase
            assertTrue(preview is ScanUiState.Phase.Preview)
            assertEquals("Local Coke", (preview as ScanUiState.Phase.Preview).candidate.name)
        }
    }

    @Test
    fun onBarcodeDecoded_whileLoading_secondBarcode_cancelsFirst() = runTest {
        fake.suspendOnLookup = true
        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("111")
            assertEquals("111", (awaitItem().phase as ScanUiState.Phase.Loading).barcode)
            vm.onBarcodeDecoded("222")
            assertEquals("222", (awaitItem().phase as ScanUiState.Phase.Loading).barcode)
            // First lookup was cancelled; assert via cancelledLookups tracking.
            assertEquals(listOf("111"), fake.cancelledLookups)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun dismissPreview_duringLoading_cancelsLookup_andReturnsToIdle() = runTest {
        fake.suspendOnLookup = true
        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("111")
            assertTrue(awaitItem().phase is ScanUiState.Phase.Loading)
            vm.dismissPreview()
            assertEquals(ScanUiState.Phase.Idle, awaitItem().phase)
            // No completion happened — lookup was cancelled.
            assertEquals(emptyList<String>(), fake.completedLookups)
            assertEquals(listOf("111"), fake.cancelledLookups)
            // No Phase.Error stamped on top of the dismissed Idle — `runCatching`
            // would catch the CancellationException and race with this Idle write.
            // We use try/catch with explicit CancellationException rethrow instead.
            expectNoEvents()
        }
    }

    @Test
    fun submitManualEntry_blankName_isNoOp() = runTest {
        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("444")
            awaitItem() // Loading
            assertTrue(awaitItem().phase is ScanUiState.Phase.ManualEntry)
            vm.submitManualEntry(name = "   ", initialQuantity = 1)
            expectNoEvents()
            assertTrue(vm.uiState.value.phase is ScanUiState.Phase.ManualEntry)
        }
        assertEquals(0, fake.addedProducts.size)
    }

    @Test
    fun submitManualEntry_nonPositiveQuantity_isNoOp() = runTest {
        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("555")
            awaitItem()
            assertTrue(awaitItem().phase is ScanUiState.Phase.ManualEntry)
            vm.submitManualEntry(name = "Cinnamon", initialQuantity = 0)
            expectNoEvents()
            vm.submitManualEntry(name = "Cinnamon", initialQuantity = -3)
            expectNoEvents()
        }
        assertEquals(0, fake.addedProducts.size)
    }

    @Test
    fun submitManualEntry_validInputs_addsProduct_andReturnsIdle() = runTest {
        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("555")
            awaitItem() // Loading
            assertTrue(awaitItem().phase is ScanUiState.Phase.ManualEntry)
            vm.submitManualEntry(name = " Cinnamon ", initialQuantity = 2)
            assertEquals(ScanUiState.Phase.Idle, awaitItem().phase)
        }
        assertEquals(1, fake.addedProducts.size)
        val added = fake.addedProducts.last()
        assertEquals("Cinnamon", added.name)
        assertEquals("555", added.barcode)
        assertEquals(2, added.initialQuantity)
        assertNull(added.brand)
        assertNull(added.imageUrl)
    }

    @Test
    fun setQuantity_inManualEntryPhase_updatesPendingQuantity() = runTest {
        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("666")
            awaitItem() // Loading
            val manual = awaitItem().phase as ScanUiState.Phase.ManualEntry
            assertEquals(1, manual.pendingQuantity)
            vm.setQuantity(4)
            assertEquals(4, (awaitItem().phase as ScanUiState.Phase.ManualEntry).pendingQuantity)
        }
    }

    // ---- I2: Exception-path tests ----

    @Test
    fun onBarcodeDecoded_repositoryThrows_transitionsToError() = runTest {
        fake.lookupShouldThrow = RuntimeException("DB exploded")
        vm.uiState.test {
            awaitItem() // Idle
            vm.onBarcodeDecoded("111")
            awaitItem() // Loading
            val state = awaitItem()
            val error = state.phase as ScanUiState.Phase.Error
            assertTrue(error.message.contains("DB exploded"))
        }
    }

    @Test
    fun confirm_applyDeltaThrows_transitionsToError() = runTest {
        val now = Clock.System.now()
        fake.seed(Product(id = 1, barcode = "x", name = "P", quantity = 0,
            createdAt = now, updatedAt = now))
        fake.applyDeltaShouldThrow = RuntimeException("disk full")

        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("x")
            awaitItem() // Loading
            awaitItem() // Preview
            vm.confirm()
            val state = awaitItem()
            val error = state.phase as ScanUiState.Phase.Error
            assertTrue(error.message.contains("disk full"))
        }
    }

    @Test
    fun submitManualEntry_addNewThrows_transitionsToError() = runTest {
        fake.addShouldThrow = RuntimeException("constraint violation")

        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("777")
            awaitItem() // Loading
            assertTrue(awaitItem().phase is ScanUiState.Phase.ManualEntry)
            vm.submitManualEntry(name = "Widget", initialQuantity = 1)
            val state = awaitItem()
            val error = state.phase as ScanUiState.Phase.Error
            assertTrue(error.message.contains("constraint violation"))
        }
    }

    // ---- I4: Dismiss-then-rescan ----

    @Test
    fun onBarcodeDecoded_afterDismissingPreview_sameBarcode_refiresLookup() = runTest {
        val now = Clock.System.now()
        fake.seed(Product(id = 1, barcode = "111", name = "Coke",
            quantity = 0, createdAt = now, updatedAt = now))

        vm.uiState.test {
            awaitItem() // Idle

            vm.onBarcodeDecoded("111")
            awaitItem() // Loading
            awaitItem() // Preview
            assertEquals(1, fake.lookupCallCount)

            vm.dismissPreview()
            assertEquals(ScanUiState.Phase.Idle, awaitItem().phase)

            vm.onBarcodeDecoded("111")
            awaitItem() // Loading
            awaitItem() // Preview
            assertEquals(2, fake.lookupCallCount)
        }
    }

    // ---- Remove-mode tests ----

    @Test
    fun remove_localHit_setQuantityClampsBothBounds() = runTest {
        val now = Clock.System.now()
        val product = Product(id = 1, barcode = "111", name = "Coke", quantity = 5,
            createdAt = now, updatedAt = now)
        val repo = FakeProductRepository()
        repo.seed(product)
        val vm = ScanViewModel(repo, initialMode = ScanMode.Remove)

        vm.uiState.test {
            awaitItem() // initial Idle
            vm.onBarcodeDecoded("111")
            awaitItem() // Loading
            val state = awaitItem() // Preview
            val phase = state.phase as ScanUiState.Phase.Preview
            assertEquals(1, phase.pendingQuantity)

            // Upper clamp: setQuantity above current quantity (5) clamps to 5.
            vm.setQuantity(99)
            assertEquals(5, (awaitItem().phase as ScanUiState.Phase.Preview).pendingQuantity)

            // Lower clamp: setQuantity below 1 clamps to 1 (Remove mode goes through
            // coerceIn(1, max), a different code path from Add mode's coerceAtLeast(1)).
            vm.setQuantity(0)
            assertEquals(1, (awaitItem().phase as ScanUiState.Phase.Preview).pendingQuantity)
        }
    }

    @Test
    fun remove_localMiss_yieldsNotInInventory_andDoesNotCallLookupForPreview() = runTest {
        val repo = FakeProductRepository()
        val vm = ScanViewModel(repo, initialMode = ScanMode.Remove)

        vm.uiState.test {
            awaitItem() // Idle
            vm.onBarcodeDecoded("222")
            awaitItem() // Loading
            val state = awaitItem()
            val phase = state.phase
            assertTrue("expected NotInInventory, was $phase", phase is ScanUiState.Phase.NotInInventory)
            assertEquals("222", (phase as ScanUiState.Phase.NotInInventory).barcode)
            // Remove mode skips lookupForPreview (which calls OFF) entirely
            assertEquals(0, repo.lookupCallCount)
        }
    }

    @Test
    fun remove_notInInventory_switchToAdd_flipsModeAndReresolves() = runTest {
        val repo = FakeProductRepository()
        repo.lookupResponses["333"] = ScanCandidate.FromOff(
            barcode = "333", name = "Pepsi", brand = "PepsiCo", imageUrl = "https://x",
        )
        val vm = ScanViewModel(repo, initialMode = ScanMode.Remove)

        vm.uiState.test {
            awaitItem() // Idle
            vm.onBarcodeDecoded("333")
            awaitItem() // Loading
            assertTrue(awaitItem().phase is ScanUiState.Phase.NotInInventory)

            vm.onSwitchToAdd()
            awaitItem() // Loading again (re-resolving in Add mode)
            val preview = awaitItem().phase as ScanUiState.Phase.Preview

            assertEquals(ScanMode.Add, vm.uiState.value.mode)
            assertEquals("Pepsi", (preview.candidate as ScanCandidate.FromOff).name)
            // Exactly one Add-path lookup fired — no double-fire from a stale Loading.
            assertEquals(1, repo.lookupCallCount)
        }
    }

    @Test
    fun remove_confirm_appliesNegativeDelta() = runTest {
        val now = Clock.System.now()
        val product = Product(id = 7, barcode = "444", name = "Milk", quantity = 3,
            createdAt = now, updatedAt = now)
        val repo = FakeProductRepository()
        repo.seed(product)
        val vm = ScanViewModel(repo, initialMode = ScanMode.Remove)

        vm.uiState.test {
            awaitItem() // Idle
            vm.onBarcodeDecoded("444")
            awaitItem() // Loading
            awaitItem() // Preview (pendingQuantity = 1)
            vm.setQuantity(2)
            awaitItem()
            vm.confirm()
            assertEquals(ScanUiState.Phase.Idle, awaitItem().phase)
        }
        assertEquals(listOf(7L to -2), repo.deltaCalls)
    }

    @Test
    fun remove_confirmAllOfQuantity_appliesFullNegativeDelta() = runTest {
        // Boundary case: user removes everything in stock (the common "I'm using up
        // the last of this" UX). pendingQuantity == current quantity.
        val now = Clock.System.now()
        val product = Product(id = 9, barcode = "555", name = "Sugar", quantity = 5,
            createdAt = now, updatedAt = now)
        val repo = FakeProductRepository()
        repo.seed(product)
        val vm = ScanViewModel(repo, initialMode = ScanMode.Remove)

        vm.uiState.test {
            awaitItem() // Idle
            vm.onBarcodeDecoded("555")
            awaitItem() // Loading
            awaitItem() // Preview pendingQuantity=1
            vm.setQuantity(5) // remove all 5 of 5
            awaitItem()
            vm.confirm()
            assertEquals(ScanUiState.Phase.Idle, awaitItem().phase)
        }
        assertEquals(listOf(9L to -5), repo.deltaCalls)
    }

    @Test
    fun remove_localHit_quantityZero_yieldsNotInInventory() = runTest {
        // Depleted row (quantity 0) routes to NotInInventory, not Preview — prevents
        // the silent-no-op stepper-at-0 confirm bug. The Switch-to-Add affordance is
        // the right next-step for a depleted row.
        val now = Clock.System.now()
        val depleted = Product(id = 11, barcode = "666", name = "Salt", quantity = 0,
            createdAt = now, updatedAt = now)
        val repo = FakeProductRepository()
        repo.seed(depleted)
        val vm = ScanViewModel(repo, initialMode = ScanMode.Remove)

        vm.uiState.test {
            awaitItem() // Idle
            vm.onBarcodeDecoded("666")
            awaitItem() // Loading
            val phase = awaitItem().phase
            assertTrue("expected NotInInventory (quantity=0), was $phase",
                phase is ScanUiState.Phase.NotInInventory)
            assertEquals("666", (phase as ScanUiState.Phase.NotInInventory).barcode)
        }
        assertEquals(0, repo.deltaCalls.size) // no removal attempted
    }

    @Test
    fun confirm_fromNotInInventoryPhase_isNoOp() = runTest {
        // confirm() guards on `as? Preview ?: return`. From NotInInventory it must
        // be a true no-op — no applyDelta, no addNew, no state change.
        val repo = FakeProductRepository()
        val vm = ScanViewModel(repo, initialMode = ScanMode.Remove)

        vm.uiState.test {
            awaitItem() // Idle
            vm.onBarcodeDecoded("777")
            awaitItem() // Loading
            val phaseBefore = awaitItem().phase // NotInInventory
            assertTrue(phaseBefore is ScanUiState.Phase.NotInInventory)

            vm.confirm() // must no-op
            expectNoEvents()
            assertEquals(phaseBefore, vm.uiState.value.phase)
        }
        assertEquals(0, repo.deltaCalls.size)
    }

    @Test
    fun onSwitchToAdd_fromNonNotInInventoryPhase_isNoOp() = runTest {
        // onSwitchToAdd guards on `as? NotInInventory ?: return`. From Idle and Preview
        // it must be a true no-op — mode stays, phase stays, no lookup fires.
        val now = Clock.System.now()
        val product = Product(id = 13, barcode = "888", name = "Tea", quantity = 2,
            createdAt = now, updatedAt = now)
        val repo = FakeProductRepository()
        repo.seed(product)
        val vm = ScanViewModel(repo, initialMode = ScanMode.Remove)

        vm.uiState.test {
            awaitItem() // Idle

            // From Idle: must no-op.
            vm.onSwitchToAdd()
            expectNoEvents()
            assertEquals(ScanMode.Remove, vm.uiState.value.mode)

            // Decode → Preview, then onSwitchToAdd from Preview: must also no-op.
            vm.onBarcodeDecoded("888")
            awaitItem() // Loading
            val previewState = awaitItem()
            assertTrue(previewState.phase is ScanUiState.Phase.Preview)

            vm.onSwitchToAdd()
            expectNoEvents()
            assertEquals(ScanMode.Remove, vm.uiState.value.mode)
            assertEquals(previewState.phase, vm.uiState.value.phase)
        }
        // lookupForPreview should never have fired (Remove mode skips it; Add path
        // never started because onSwitchToAdd no-opped both times).
        assertEquals(0, repo.lookupCallCount)
    }

    // ---- onCameraError: contract is "wrap reason with Couldn't open camera: prefix
    //      and cancel any in-flight scan jobs". ----

    @Test
    fun onCameraError_wrapsReasonWithCouldntOpenCameraPrefix() = runTest {
        vm.uiState.test {
            awaitItem() // Idle
            vm.onCameraError("device busy")
            val state = awaitItem()
            val error = state.phase as ScanUiState.Phase.Error
            assertEquals("Couldn't open camera: device busy", error.message)
        }
    }

    @Test
    fun onCameraError_cancelsInFlightLookupJob() = runTest {
        fake.suspendOnLookup = true

        vm.uiState.test {
            awaitItem() // Idle
            vm.onBarcodeDecoded("12345")
            awaitItem() // Loading (the lookup is now suspended in awaitCancellation)
            vm.onCameraError("camera died")
            val state = awaitItem()
            assertTrue(state.phase is ScanUiState.Phase.Error)
            assertEquals("Couldn't open camera: camera died",
                (state.phase as ScanUiState.Phase.Error).message)
        }
        // FakeProductRepository.suspendOnLookup records every cancelled barcode.
        assertTrue("expected in-flight lookup for 12345 to be cancelled by onCameraError",
            "12345" in fake.cancelledLookups)
    }

    // --- #30 / SR-13: input sanitization at the scan boundary ---

    @Test
    fun onBarcodeDecoded_rtlOverridePrefix_sanitizesBeforeDispatch() = runTest {
        // ML Kit decode of a barcode behind an RTL-override codepoint must not
        // forward the override to the repository (where it would mis-render in
        // Compose Text(barcode) and pollute the URL path / Room column).
        vm.uiState.test {
            awaitItem() // Idle
            vm.onBarcodeDecoded("‮5449000000996")
            awaitItem() // Loading
            awaitItem() // ManualEntry (no seed → local miss + OFF miss)
        }
        assertEquals(listOf("5449000000996"), fake.completedLookups)
    }

    @Test
    fun onBarcodeDecoded_newlineInjection_sanitizesBeforeDispatch() = runTest {
        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("5449\n000000996")
            awaitItem() // Loading
            awaitItem() // ManualEntry
        }
        assertEquals(listOf("5449000000996"), fake.completedLookups)
    }

    @Test
    fun onBarcodeDecoded_onlyControlChars_isIgnoredWithoutDispatch() = runTest {
        // Sanitize collapses to empty, which the blank-guard treats as a no-op
        // — same as a low-confidence ML Kit frame producing a blank decode.
        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("\u0000\u0007‮\n\r\u007f")
            expectNoEvents()
        }
        assertEquals(0, fake.lookupCallCount)
    }

    @Test
    fun onBarcodeDecoded_nonBlankCollapsedToBlank_logsInfoWithLengthOnly() = runTest {
        // A non-blank input that sanitize collapses to empty is anomalous —
        // ML Kit shouldn't emit C0/C1/RTL for the EAN/UPC formats. We want
        // *one* INFO record so the case is auditable, but the log carries the
        // input length only (no content — by definition hostile or corrupt).
        JulLogCapture("ScanViewModel").use { capture ->
            vm.uiState.test {
                awaitItem()
                vm.onBarcodeDecoded("\u0000\u0007‮\n\r\u007f")
                expectNoEvents()
            }
            val joined = capture.messages().joinToString(" | ")
            assertTrue("expected fully-stripped INFO record: $joined", joined.contains("fully stripped"))
            assertTrue("expected len=6 marker: $joined", joined.contains("len=6"))
            assertFalse("RLO leaked into log: $joined", joined.contains("\u202e"))
        }
    }

    @Test
    fun onBarcodeDecoded_blankInput_doesNotLog() = runTest {
        // The pre-existing low-confidence-frame case must stay silent —
        // emitting an INFO at the camera frame rate would spam logcat.
        JulLogCapture("ScanViewModel").use { capture ->
            vm.uiState.test {
                awaitItem()
                vm.onBarcodeDecoded("")
                vm.onBarcodeDecoded("   ")
                expectNoEvents()
            }
            assertTrue(
                "expected zero log records, got: ${capture.messages()}",
                capture.messages().isEmpty(),
            )
        }
    }

    // --- #31 / SR-3,4,11: log redaction ---

    @Test
    fun resolveBarcode_failure_logsHintNotFullBarcode() = runTest {
        fake.lookupShouldThrow = RuntimeException("simulated repository failure")
        JulLogCapture("ScanViewModel").use { capture ->
            vm.uiState.test {
                awaitItem() // Idle
                vm.onBarcodeDecoded("5449000000996")
                awaitItem() // Loading
                awaitItem() // Error
            }
            val joined = capture.messages().joinToString(" | ")
            assertTrue("expected hint '5449…96' in log: $joined", joined.contains("5449…96"))
            assertFalse(
                "full barcode '5449000000996' leaked into log: $joined",
                joined.contains("5449000000996"),
            )
        }
    }

    @Test
    fun confirm_failure_doesNotLogScanCandidateContents() = runTest {
        // SR-3: the prior log used `phase=$phase`, which serialised the entire
        // ScanCandidate via data-class toString (barcode + name + brand + imageUrl).
        // The redaction replaces it with `phaseType=Preview` and nothing else.
        fake.lookupResponses["5449000000996"] = ScanCandidate.FromOff(
            barcode = "5449000000996",
            name = "Sensitive Brand-Name Product",
            brand = "Sensitive Brand Co.",
            imageUrl = "https://images.example/secret-asset.jpg",
        )
        fake.addShouldThrow = RuntimeException("simulated DB write failure")

        JulLogCapture("ScanViewModel").use { capture ->
            vm.uiState.test {
                awaitItem() // Idle
                vm.onBarcodeDecoded("5449000000996")
                awaitItem() // Loading
                awaitItem() // Preview
                vm.confirm()
                awaitItem() // Error
            }
            val joined = capture.messages().joinToString(" | ")
            assertFalse("barcode leaked: $joined", joined.contains("5449000000996"))
            assertFalse("product name leaked: $joined", joined.contains("Sensitive Brand-Name Product"))
            assertFalse("brand leaked: $joined", joined.contains("Sensitive Brand Co."))
            assertFalse("image URL leaked: $joined", joined.contains("secret-asset.jpg"))
        }
    }

    @Test
    fun submitManualEntry_failure_doesNotLogName() = runTest {
        // SR-4: the prior log included `name=$trimmed` — leaks user-typed
        // product names (often household-specific) into logcat.
        fake.addShouldThrow = RuntimeException("simulated DB write failure")

        JulLogCapture("ScanViewModel").use { capture ->
            vm.uiState.test {
                awaitItem() // Idle
                vm.onBarcodeDecoded("0000000000000") // unknown → ManualEntry
                awaitItem() // Loading
                awaitItem() // ManualEntry
                vm.submitManualEntry(name = "Private Household Inventory", initialQuantity = 1)
                awaitItem() // Error
            }
            val joined = capture.messages().joinToString(" | ")
            assertFalse("user-typed name leaked: $joined", joined.contains("Private Household Inventory"))
        }
    }

    private class FakeProductRepository : ProductRepository {
        private val byBarcode = mutableMapOf<String, Product>()
        var lastDelta: Pair<Long, Int>? = null

        // Responses can now be ScanCandidate directly
        var lookupResponses = mutableMapOf<String, ScanCandidate?>()
        var lookupCallCount = 0
        var suspendOnLookup = false
        val completedLookups = mutableListOf<String>()
        val cancelledLookups = mutableListOf<String>()

        // I2: exception injection
        var lookupShouldThrow: Throwable? = null
        var addShouldThrow: Throwable? = null
        var applyDeltaShouldThrow: Throwable? = null

        fun seed(p: Product) {
            byBarcode[p.barcode!!] = p
        }

        override fun observeProducts(): Flow<List<Product>> =
            MutableStateFlow(emptyList<Product>()).asStateFlow()
        override fun search(query: String): Flow<List<Product>> =
            MutableStateFlow(emptyList<Product>()).asStateFlow()
        override fun observeById(id: Long): Flow<Product?> =
            MutableStateFlow(byBarcode.values.firstOrNull { it.id == id }).asStateFlow()
        override suspend fun findById(id: Long): Product? =
            byBarcode.values.firstOrNull { it.id == id }
        override suspend fun findLocalByBarcode(code: String): Product? = byBarcode[code]
        override suspend fun lookupForPreview(code: String): ScanCandidate? {
            lookupShouldThrow?.let { throw it }
            lookupCallCount++
            if (suspendOnLookup) {
                try {
                    kotlinx.coroutines.awaitCancellation() // never returns until job is cancelled
                } catch (e: kotlinx.coroutines.CancellationException) {
                    cancelledLookups += code
                    throw e
                }
            }
            // explicit lookup response takes priority; otherwise fall back to seeded local row
            val candidate = if (lookupResponses.containsKey(code)) {
                lookupResponses[code]
            } else {
                byBarcode[code]?.let { ScanCandidate.Persisted(it) }
            }
            completedLookups += code
            return candidate
        }

        data class AddCall(
            val name: String,
            val brand: String?,
            val barcode: String?,
            val imageUrl: String?,
            val initialQuantity: Int,
        )
        val addedProducts = mutableListOf<AddCall>()

        override suspend fun addNew(
            name: String, brand: String?, barcode: String?, imageUrl: String?,
            initialQuantity: Int,
        ): Long {
            addShouldThrow?.let { throw it }
            addedProducts += AddCall(name, brand, barcode, imageUrl, initialQuantity)
            return (addedProducts.size).toLong()
        }
        val deltaCalls = mutableListOf<Pair<Long, Int>>()

        override suspend fun applyDelta(productId: Long, delta: Int) {
            applyDeltaShouldThrow?.let { throw it }
            val pair = productId to delta
            lastDelta = pair
            deltaCalls += pair
        }
        override suspend fun rename(productId: Long, newName: String) = Unit
        override suspend fun delete(productId: Long) = Unit
    }
}
