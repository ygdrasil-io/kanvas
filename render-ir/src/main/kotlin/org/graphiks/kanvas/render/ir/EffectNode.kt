package org.graphiks.kanvas.render.ir

/** Root of backend-neutral effects. Concrete effects arrive in Task 10. */
public sealed interface EffectNode : CanonicalValue

/** Ordered effect axis for a draw. The empty stack is the only foundational value. */
public sealed interface EffectStack : CanonicalValue {
    public data object Empty : EffectStack {
        override val canonicalId: CanonicalId = canonicalId("effect-stack-empty-v1")
    }
}
