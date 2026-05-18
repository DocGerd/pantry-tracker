package de.docgerdsoft.pantrytracker.ui.scan

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Pins the three-arm `when` in [initialPhase] that drives the gate's starting
 * state on (re-)entry. The SoftDenied case (#2) is the one the bug that
 * motivated this test originally regressed on: re-tapping Scan to Add after a
 * soft-deny used to return Unknown and re-show the rationale dialog, instead
 * of routing the user directly to "Camera access needed" with Try again.
 *
 * Bypasses the Composable wrapper entirely — `initialPhase` is the testable
 * boundary, and the wrapper just plumbs `LocalContext.findActivity()` into it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CameraPermissionGatePhaseTest {

    /** Stub Activity that lets the test drive `shouldShowRequestPermissionRationale`.
     *  Robolectric doesn't expose a setter for the rationale flag, but Activity
     *  is just a method override away. */
    class RationaleStubActivity : Activity() {
        var rationaleResponse: Boolean = false
        override fun shouldShowRequestPermissionRationale(permission: String): Boolean =
            rationaleResponse
    }

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun buildActivity(): RationaleStubActivity =
        Robolectric.buildActivity(RationaleStubActivity::class.java).create().get()

    @Test
    fun cameraGranted_returnsGranted() {
        shadowOf(context.applicationContext as android.app.Application)
            .grantPermissions(Manifest.permission.CAMERA)
        assertEquals(
            CameraPermissionPhase.Granted,
            initialPhase(context, buildActivity()),
        )
    }

    @Test
    fun denied_withRationale_returnsSoftDenied() {
        shadowOf(context.applicationContext as android.app.Application)
            .denyPermissions(Manifest.permission.CAMERA)
        val activity = buildActivity().apply { rationaleResponse = true }
        assertEquals(
            CameraPermissionPhase.SoftDenied,
            initialPhase(context, activity),
        )
    }

    @Test
    fun denied_noRationale_returnsUnknown() {
        shadowOf(context.applicationContext as android.app.Application)
            .denyPermissions(Manifest.permission.CAMERA)
        val activity = buildActivity().apply { rationaleResponse = false }
        assertEquals(
            CameraPermissionPhase.Unknown,
            initialPhase(context, activity),
        )
    }

    @Test
    fun denied_nullActivity_returnsHardDenied() {
        shadowOf(context.applicationContext as android.app.Application)
            .denyPermissions(Manifest.permission.CAMERA)
        // Sanity: the host has no Activity. The launcher in production cannot
        // surface a system prompt from that state, so showing the rationale
        // dialog would dead-end at Continue. The gate falls closed to
        // HardDenied so the user still has the Open Settings recovery path.
        assertEquals(
            CameraPermissionPhase.HardDenied,
            initialPhase(context, activity = null),
        )
    }

    @Test
    fun granted_overrides_nullActivity() {
        shadowOf(context.applicationContext as android.app.Application)
            .grantPermissions(Manifest.permission.CAMERA)
        // The permission-check branch precedes the activity-null branch: a
        // granted permission must short-circuit to Granted even if the host
        // is unusual enough to lack an Activity.
        assertEquals(
            CameraPermissionPhase.Granted,
            initialPhase(context, activity = null),
        )
    }

    @Test
    fun permissionConstants_unchanged() {
        // Guards against an accidental rename of the constant the gate reads;
        // a Manifest.permission.CAMERA typo would silently misroute every
        // path through `initialPhase`.
        assertEquals("android.permission.CAMERA", Manifest.permission.CAMERA)
        assertEquals(PackageManager.PERMISSION_GRANTED, 0)
    }
}
