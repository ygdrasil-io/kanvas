package org.graphiks.kanvas.dsl

/**
 * Marks a class as a Kanvas DSL receiver type, enabling Kotlin's [DslMarker]
 * compile-time scoping to prevent accidental leakage of implicit receivers
 * across nested builder scopes.
 */
@DslMarker
annotation class KanvasDsl
