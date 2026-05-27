package de.docgerdsoft.pantrytracker.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

/**
 * Registers Pantry Tracker's custom detekt rules as the `pantry` rule set.
 *
 * Detekt discovers this provider via the service-loader mechanism: the file
 * `resources/META-INF/services/io.gitlab.arturbosch.detekt.api.RuleSetProvider`
 * (placed under `src/test/resources/`) lists this fully-qualified class name.
 *
 * Rules in this set:
 * - [ErrorToneRule] — enforces "Couldn't <verb>: ..." prefix on all
 *   user-facing error messages passed to Snackbar / Toast call sites.
 */
class PantryRuleSetProvider : RuleSetProvider {

    override val ruleSetId: String = "pantry"

    override fun instance(config: Config): RuleSet = RuleSet(
        id = ruleSetId,
        rules = listOf(ErrorToneRule(config.subConfig("ErrorTone"))),
    )
}
