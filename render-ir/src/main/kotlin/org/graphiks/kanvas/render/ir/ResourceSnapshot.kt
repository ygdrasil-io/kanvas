package org.graphiks.kanvas.render.ir

/** Stable identifier for a logical resource, never a renderer handle. */
@JvmInline
public value class ResourceId(public val value: String) {
    init {
        require(value.isNotBlank()) { "ResourceId.value must not be blank" }
    }
}

/** Foundational logical resource contract. Concrete snapshots arrive in Task 10. */
public sealed interface ResourceSnapshot : CanonicalValue {
    public data object None : ResourceSnapshot {
        override val canonicalId: CanonicalId = canonicalId("resource-none-v1")
    }
}

/** A resolved logical resource reference, never a backend allocation. */
public data class ResourceReference(public val id: ResourceId) : CanonicalValue {
    override val canonicalId: CanonicalId = canonicalId("resource-reference-v1", id.value)
}
