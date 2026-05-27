package de.docgerdsoft.pantrytracker.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getCallNameExpression

/**
 * Detekt rule enforcing the "Couldn't <verb>: ..." user-facing error convention.
 *
 * Every string literal passed as the `message` argument to
 * [SnackbarHostState.showSnackbar], [Snackbar.show], or [Toast.makeText] must
 * start with the prefix `"Couldn't "`. String template expressions (those whose
 * first literal part starts with `"Couldn't "`) are accepted. Non-literal
 * expressions (variables, function calls) are not checked — the rule is
 * conservative and only flags what it can statically verify.
 *
 * ## Rationale
 * The convention is documented in `CLAUDE.md` "Things that have bitten past
 * sessions". Before SR-78 it was only a human-visible convention; this rule
 * encodes it as a static gate so new error paths can't regress silently.
 *
 * ## Scope of flagged call sites
 * - `SnackbarHostState.showSnackbar(message = "...", ...)`
 * - `SnackbarHostState.showSnackbar("...", ...)`  (positional first arg)
 * - `Toast.makeText(context, "...", ...)` (second positional arg = message)
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
     * Names of call expressions that carry a user-visible error `message`.
     * Each entry maps to the zero-based index of the `message` positional arg,
     * or -1 when the call exclusively uses the named parameter `message =`.
     */
    private val checkedFunctions = mapOf(
        "showSnackbar" to 0,
        "makeText" to 1, // Toast.makeText(context, message, duration)
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        val name = expression.getCallNameExpression()?.text ?: return
        val messageArgIndex = checkedFunctions[name] ?: return

        val messageArg = resolveMessageArg(expression, messageArgIndex) ?: return
        val literal = extractFirstStringLiteral(messageArg) ?: return

        if (!literal.startsWith(REQUIRED_PREFIX)) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(messageArg),
                    message = "Error message \"$literal\" must start with " +
                        "\"$REQUIRED_PREFIX\". Use \"Couldn't <verb>: <reason>\" instead.",
                ),
            )
        }
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
     * Extracts the leading string literal text from a [KtValueArgument] whose
     * expression is either a plain string literal or a string template whose
     * first entry is a literal part.
     *
     * Returns `null` when the argument is a variable reference, a call
     * expression, or any other non-literal form we cannot statically evaluate —
     * those are intentionally left unchecked (conservative).
     */
    private fun extractFirstStringLiteral(arg: KtValueArgument): String? {
        val expr = arg.getArgumentExpression() ?: return null
        if (expr !is KtStringTemplateExpression) return null
        val entries = expr.entries
        if (entries.isEmpty()) return null
        // A plain string literal has a single KtLiteralStringTemplateEntry.
        // A template like "Couldn't save: ${e.message}" has a literal first entry
        // whose text is "Couldn't save: " — we only need the prefix check.
        return entries.first().text
    }

    private companion object {
        private const val REQUIRED_PREFIX = "Couldn't "
    }
}
