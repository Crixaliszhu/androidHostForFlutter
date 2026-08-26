package com.example.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

class ProjectRuleSetProvider : RuleSetProvider {
    override val ruleSetId: String = "project-rules"

    override fun instance(config: Config): RuleSet {
        return RuleSet(
            ruleSetId,
            listOf(DataClassContractRule(config))
        )
    }
}
