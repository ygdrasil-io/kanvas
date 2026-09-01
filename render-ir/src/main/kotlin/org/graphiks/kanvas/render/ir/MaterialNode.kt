package org.graphiks.kanvas.render.ir

/** Root of the backend-neutral material graph. Concrete graph variants arrive in Task 10. */
public sealed interface MaterialNode : CanonicalValue {
    /** A neutral material that contributes no source colour. */
    public data object Transparent : MaterialNode {
        override val canonicalId: CanonicalId = canonicalId("material-transparent-v1")
    }
}

/** Semantic coverage request, deliberately separate from a GPU coverage strategy. */
public enum class CoverageRequest : CanonicalValue {
    DEFAULT,
    ANTIALIASED,
    HARD_EDGE;

    override val canonicalId: CanonicalId get() = canonicalId("coverage-request-v1", name)
}

/** Neutral blend axis; Task 10 adds the public blend variants. */
public sealed interface BlendNode : CanonicalValue {
    public data object SrcOver : BlendNode {
        override val canonicalId: CanonicalId = canonicalId("blend-src-over-v1")
    }
}

/** Neutral ordered clip axis; Task 10 adds clip operations. */
public sealed interface ClipStackNode : CanonicalValue {
    public data object Empty : ClipStackNode {
        override val canonicalId: CanonicalId = canonicalId("clip-stack-empty-v1")
    }
}
