package de.docgerdsoft.pantrytracker.ui.common

import android.content.Context
import androidx.annotation.StringRes

/**
 * A locale-agnostic, resolvable piece of user-facing text.
 *
 * ViewModels emit `UiText` instead of a finished `String` so they stay free of
 * `Context`/`Resources` (Android architecture guidance: keep VMs framework-free
 * and pure-JVM-testable). The UI layer resolves it to a `String` at the
 * Compose/Toast boundary via [resolve], where a `Context` is legitimately
 * available.
 *
 * [Res] carries a string-resource id plus already-`UiText` format args (so a
 * nested fallback like `Res(error_unknown_reason)` localizes too). [Raw] wraps
 * an already-resolved runtime string (e.g. an exception message) with no
 * resource id.
 *
 * [resolve] is deliberately a plain function taking `context` as a *parameter*
 * (not a `@Composable` reading `LocalContext.current`): per CLAUDE.md this is
 * the documented escape from the `LocalContextGetResourceValueCall` lint — the
 * same pattern `RelativeTime.format(context, …)` uses — so it is safe to call
 * from inside a `LaunchedEffect`/coroutine as well as from composition.
 */
sealed interface UiText {

    /** An already-resolved runtime string with no resource id (e.g. an
     *  exception message). */
    data class Raw(val value: String) : UiText

    /** A string resource plus format args (themselves `UiText`, resolved first).
     *  Args resolve to strings, so the [id] template must use `%s` placeholders and
     *  `args.size` must equal the template's placeholder count. */
    data class Res(
        @param:StringRes val id: Int,
        val args: List<UiText> = emptyList(),
    ) : UiText

    // Dynamic format args require a vararg spread; the arg lists here are tiny
    // (≤1 element), so the array-copy cost the SpreadOperator rule warns about is
    // negligible.
    @Suppress("SpreadOperator")
    fun resolve(context: Context): String = when (this) {
        is Raw -> value
        is Res ->
            if (args.isEmpty()) {
                context.getString(id)
            } else {
                context.getString(id, *args.map { it.resolve(context) }.toTypedArray())
            }
    }

    companion object {
        /**
         * Builds a "Couldn't <verb>: <cause>" [Res]: the [messageId] template
         * formatted with [cause]'s message, or the localized [unknownReasonId]
         * fallback when that message is null. Centralizes the one-arg + fallback
         * idiom shared by the ViewModel error helpers; [unknownReasonId] is a
         * parameter (not hardcoded) so this generic type stays free of app `R`.
         */
        fun causedError(
            @StringRes messageId: Int,
            @StringRes unknownReasonId: Int,
            cause: Throwable,
        ): UiText = Res(messageId, listOf(cause.message?.let { Raw(it) } ?: Res(unknownReasonId)))
    }
}
