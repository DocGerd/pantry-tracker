package de.docgerdsoft.pantrytracker.detekt

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proof test for [ErrorToneRule].
 *
 * The pre-SR-78 wiring compiled the rule into :app's unit-test source set and
 * pointed `detektPlugins(files(...))` at AGP intermediates — the rule LOADED
 * but never FIRED (empirically: a deliberate "Could not delete:" violation gave
 * BUILD SUCCESSFUL with zero findings). These tests exist so "inert" can never
 * silently regress: each asserts the rule's actual firing behaviour using
 * detekt's [lint] harness (no type resolution — the rule is purely PSI-based).
 *
 * `lint(...)` parses the snippet, runs the rule, and returns the findings; the
 * count is the contract under test.
 */
class ErrorToneRuleTest {

    private val rule get() = ErrorToneRule()

    // -------------------------------------------------------------------------
    // (a) MUST fire — exactly one finding on a non-conforming error message
    // -------------------------------------------------------------------------

    @Test
    fun `reports exactly one finding on a non-conforming Phase Error message`() {
        // Phase.Error is an error-ONLY sink — any non-"Couldn't" literal fires.
        val findings = rule.lint(
            """
            object Phase {
                class Error(val message: String)
            }
            fun fail() = Phase.Error("Could not read inventory: timeout")
            """.trimIndent(),
        )
        assertEquals(
            "Expected exactly one ErrorTone finding on the 'Could not read inventory' Phase.Error",
            1,
            findings.size,
        )
    }

    @Test
    fun `reports exactly one finding on a Could-not showSnackbar message`() {
        // Shared sink: fires because the literal opens with a wrong-tone error phrase.
        val findings = rule.lint(
            """
            suspend fun show(host: Host) {
                host.showSnackbar(message = "Could not save: disk full")
            }
            interface Host { suspend fun showSnackbar(message: String) }
            """.trimIndent(),
        )
        assertEquals(
            "Expected exactly one ErrorTone finding on the 'Could not save' snackbar",
            1,
            findings.size,
        )
    }

    // -------------------------------------------------------------------------
    // (b) MUST stay silent — zero findings on conforming or non-error messages
    // -------------------------------------------------------------------------

    @Test
    fun `reports zero findings on a conforming showSnackbar message`() {
        val findings = rule.lint(
            """
            suspend fun show(host: Host) {
                host.showSnackbar(message = "Couldn't save: disk full")
            }
            interface Host { suspend fun showSnackbar(message: String) }
            """.trimIndent(),
        )
        assertTrue(
            "A conforming 'Couldn't save' snackbar must not fire, got ${findings.size}",
            findings.isEmpty(),
        )
    }

    @Test
    fun `reports zero findings on a success showSnackbar message`() {
        // Regression guard: "Deleted X" is a SUCCESS snackbar on the same
        // showSnackbar API, NOT an error — it must never be flagged.
        val findings = rule.lint(
            """
            suspend fun show(host: Host, name: String) {
                host.showSnackbar(message = "Deleted ${'$'}name")
            }
            interface Host { suspend fun showSnackbar(message: String) }
            """.trimIndent(),
        )
        assertTrue(
            "A success snackbar ('Deleted …') must not fire, got ${findings.size}",
            findings.isEmpty(),
        )
    }

    @Test
    fun `reports zero findings on a conforming Phase Error message`() {
        val findings = rule.lint(
            """
            object Phase {
                class Error(val message: String)
            }
            fun ok() = Phase.Error("Couldn't read inventory: timeout")
            """.trimIndent(),
        )
        assertTrue(
            "A conforming 'Couldn't read inventory' Phase.Error must not fire, got ${findings.size}",
            findings.isEmpty(),
        )
    }

    @Test
    fun `accepts a conforming literal-prefixed template`() {
        val findings = rule.lint(
            """
            suspend fun show(host: Host, e: Exception) {
                host.showSnackbar(message = "Couldn't save: ${'$'}{e.message}")
            }
            interface Host { suspend fun showSnackbar(message: String) }
            """.trimIndent(),
        )
        assertTrue(
            "A literal-prefixed conforming template must not fire, got ${findings.size}",
            findings.isEmpty(),
        )
    }

    // -------------------------------------------------------------------------
    // Conservative skips — blind spots must NOT be flagged (no false positives)
    // -------------------------------------------------------------------------

    @Test
    fun `skips interpolation-first templates`() {
        // First template entry is an interpolation, not a literal — unchecked.
        val findings = rule.lint(
            """
            suspend fun show(host: Host, e: Exception) {
                host.showSnackbar(message = "${'$'}{e.message} failed")
            }
            interface Host { suspend fun showSnackbar(message: String) }
            """.trimIndent(),
        )
        assertTrue(
            "Interpolation-first templates must be skipped, got ${findings.size}",
            findings.isEmpty(),
        )
    }

    @Test
    fun `skips non-literal message arguments (val indirection)`() {
        val findings = rule.lint(
            """
            suspend fun show(host: Host) {
                val msg = "Could not save: disk full"
                host.showSnackbar(message = msg)
            }
            interface Host { suspend fun showSnackbar(message: String) }
            """.trimIndent(),
        )
        assertTrue(
            "A val-indirected message is a known blind spot and must be skipped, got ${findings.size}",
            findings.isEmpty(),
        )
    }

    @Test
    fun `ignores unrelated call expressions`() {
        val findings = rule.lint(
            """
            fun unrelated() {
                println("Could not save: this is not an error sink")
            }
            """.trimIndent(),
        )
        assertTrue(
            "Calls that are not error sinks must be ignored, got ${findings.size}",
            findings.isEmpty(),
        )
    }
}
