package org.graphiks.kanvas.gpu.plan

@JvmInline
public value class PlanId(public val value: String) {
    init { require(value.isNotBlank()) { "Plan ID must not be blank" } }
}

@JvmInline
public value class PlanResourceId(public val value: String) {
    init { require(value.isNotBlank()) { "Plan resource ID must not be blank" } }
}

@JvmInline
public value class PlanPassId(public val value: String) {
    init { require(value.isNotBlank()) { "Plan pass ID must not be blank" } }
}

internal fun planResourceId(role: PlanResourceRole, ordinal: Int): PlanResourceId =
    PlanResourceId("${role.name}:$ordinal")

internal fun planPassId(role: PlanPassRole, ordinal: Int): PlanPassId =
    PlanPassId("${role.name}:$ordinal")
