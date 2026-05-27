package de.docgerdsoft.pantrytracker.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getCallNameExpression

/**
 * Detekt rule enforcing the "Couldn't <verb>: ..." user-facing error convention.
 *
 * The check is purely syntactic (PSI-based, no type resolution) and deliberately
 * CONSERVATIVE: it only inspects string literals / literal-prefixed templates and
 * skips anything it cannot statically read.
 *
 * ## Two classes of sink, two checks
 * The app surfaces error copy through two kinds of call. They need different
 * handling because one is *exclusively* an error channel and the other is shared
 * with success copy:
 *
 * 1. **Error-only sinks** — `Error("...")`, matching `ScanUiState.Phase.Error(...)`,
 *    the scan flow's inline error surface. Every literal here is an error message,
 *    so the rule flags it whenever it does NOT start with `"Couldn't "`. (The
 *    call-name expression for a qualified `Phase.Error(...)` is just `Error`, so
 *    the bare name is what we key on.)
 *
 * 2. **Shared sinks** — `SnackbarHostState.showSnackbar(message = "...")` and
 *    `Toast.makeText(context, "...", ...)`. These carry BOTH success copy
 *    (`"Deleted ${name}"`) and error copy (`"Could not delete: ${name}"`), so a
 *    blanket "must start with Couldn't" check would false-positive on every
 *    success message. Instead the rule flags a shared-sink literal only when it
 *    opens with a recognised NON-conforming error phrase — `"Could not "`,
 *    `"Couldnt "`, `"Error"`, `"Failed"`, `"Unable to "`, `"Cannot "`,
 *    `"Can't "` — i.e. text that is clearly an error message written the wrong
 *    way. A conforming `"Couldn't …"` message and a non-error `"Deleted …"`
 *    message both pass untouched.
 *
 * ## Known blind spots (intentional — not enforced)
 * Indirections the rule cannot statically follow:
 * - **Indirection via `val`**: `val msg = "Could not …"; showSnackbar(msg)` — the
 *   argument is a name reference, not a literal. (e.g. [DetailViewModel] builds
 *   `error = "Couldn't $op: …"` into UI state and [DetailScreen] later passes
 *   that `String` to `showSnackbar` — the literal is one hop away and unchecked.)
 * - **String concatenation**: `showSnackbar("Could " + "not …")`.
 * - **Resource lookups**: `showSnackbar(getString(R.string.err))` — the copy
 *   lives in `strings.xml`, invisible to a PSI rule.
 * - **Interpolation-first templates**: `Error("${e.message}")` — no leading
 *   literal to check, so it is skipped (avoids a false positive).
 * - **Error copy in a shared sink with a non-standard opener**: a wrong-tone
 *   error like `showSnackbar("Oops, save broke")` is NOT caught — it matches
 *   none of the recognised error openers. The opener list is a pragmatic
 *   trade-off favouring zero false positives over exhaustive recall.
 *
 * ## Rationale
 * The convention is documented in `CLAUDE.md` "Things that have bitten past
 * sessions". Before SR-78 it was only a human-visible convention; this rule
 * encodes it as a static gate. The rule lives in the standalone `:detekt-rules`
 * module and is wired into `:app:detekt` via `detektPlugins(project(":detekt-rules"))`;
 * the `:detekt-rules:test` proof test guarantees it keeps firing.
 *
 * @see <a href="https://detekt.dev/docs/introduction/extensions/">detekt extensions</a>
 */
class ErrorToneRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "ErrorTone",
        severity = Severity.Style,
        description = "User-facing error messages must start with \"Couldn't <verb>: \".",
        debt = Debt.FIVE_MINS,
    )

    /**
     * Error-ONLY sinks: every literal message here is an error, so any
     * non-conforming prefix is a violation. Maps call-name → message arg index.
     */
    private val errorOnlySinks = mapOf(
        "Error" to 0, // ScanUiState.Phase.Error(message) — inline scan error
    )

    /**
     * SHARED sinks that carry both success and error copy: only flag literals
     * that open with a recognised wrong-tone error phrase (see [ERROR_OPENERS]).
     * Maps call-name → message arg index.
     */
    private val sharedSinks = mapOf(
        "showSnackbar" to 0,
        "makeText" to 1, // Toast.makeText(context, message, duration)
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        val name = expression.getCallNameExpression()?.text ?: return

        errorOnlySinks[name]?.let { idx ->
            checkErrorOnlySink(expression, idx)
            return
        }
        sharedSinks[name]?.let { idx ->
            checkSharedSink(expression, idx)
        }
    }

    /** Flags an error-only sink literal that does not start with "Couldn't ". */
    private fun checkErrorOnlySink(call: KtCallExpression, argIndex: Int) {
        val arg = resolveMessageArg(call, argIndex) ?: return
        val literal = extractLeadingLiteral(arg) ?: return
        if (!literal.startsWith(REQUIRED_PREFIX)) {
            reportViolation(arg, literal)
        }
    }

    /**
     * Flags a shared-sink literal ONLY when it opens with a recognised wrong-tone
     * error phrase — leaving success copy ("Deleted …") and already-conforming
     * "Couldn't …" copy untouched.
     */
    private fun checkSharedSink(call: KtCallExpression, argIndex: Int) {
        val arg = resolveMessageArg(call, argIndex) ?: return
        val literal = extractLeadingLiteral(arg) ?: return
        if (literal.startsWith(REQUIRED_PREFIX)) return // already conforming
        val looksLikeError = ERROR_OPENERS.any { literal.startsWith(it, ignoreCase = true) }
        if (looksLikeError) {
            reportViolation(arg, literal)
        }
    }

    private fun reportViolation(arg: KtValueArgument, literal: String) {
        report(
            CodeSmell(
                issue,
                Entity.from(arg),
                message = "Error message \"$literal\" must start with " +
                    "\"$REQUIRED_PREFIX\". Use \"Couldn't <verb>: <reason>\" instead.",
            ),
        )
    }

    /**
     * Returns the [KtValueArgument] carrying the `message` for this call.
     * Prefers the named `message = ...` form; falls back to positional lookup
     * at [positionalIndex].
     */
    private fun resolveMessageArg(
        call: KtCallExpression,
        positionalIndex: Int,
    ): KtValueArgument? {
        val args = call.valueArguments
        // Prefer named-parameter form first (works regardless of position).
        val named = args.firstOrNull { it.getArgumentName()?.text == "message" }
        if (named != null) return named
        // Fall back to positional.
        return args.getOrNull(positionalIndex)
    }

    /**
     * Extracts the leading literal text of a string-template argument, but ONLY
     * when that argument's first entry is a [KtLiteralStringTemplateEntry].
     *
     * Returns the literal text of the first entry for:
     * - a plain string literal (`"Couldn't save"`) — one literal entry, and
     * - a literal-prefixed template (`"Couldn't save: ${e.message}"`) — first
     *   entry is the literal `"Couldn't save: "`.
     *
     * Returns `null` (skip — unchecked) for:
     * - a non-string-template argument (variable, call, concatenation, …),
     * - an empty template (`""`) with no entries, and
     * - an interpolation-first template (`"${e.message} failed"`) whose first
     *   entry is NOT a literal — checking the raw PSI text there would
     *   false-positive on the `$`/`{` prefix, so we conservatively skip it.
     */
    private fun extractLeadingLiteral(arg: KtValueArgument): String? {
        val expr = arg.getArgumentExpression() ?: return null
        if (expr !is KtStringTemplateExpression) return null
        val first = expr.entries.firstOrNull() ?: return null
        // Only verify when we can read an actual leading literal. Interpolation
        // entries (KtSimpleNameStringTemplateEntry / KtBlockStringTemplateEntry)
        // are skipped rather than prefix-checked against their raw PSI text.
        if (first !is KtLiteralStringTemplateEntry) return null
        return first.text
    }

    private companion object {
        private const val REQUIRED_PREFIX = "Couldn't "

        /**
         * Openers that mark a SHARED-sink literal as an error message written
         * the wrong way. Matched case-insensitively. "Couldnt " (missing
         * apostrophe) is included because it is a near-miss of the convention.
         */
        private val ERROR_OPENERS = listOf(
            "Could not ",
            "Couldnt ",
            "Error",
            "Failed",
            "Unable to ",
            "Cannot ",
            "Can't ",
        )
    }
}
