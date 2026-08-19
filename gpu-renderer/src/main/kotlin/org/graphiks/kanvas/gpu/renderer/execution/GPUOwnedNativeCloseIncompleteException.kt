package org.graphiks.kanvas.gpu.renderer.execution

/** Typed signal that one owner has requested close but still retains retryable native identities. */
internal class GPUOwnedNativeCloseIncompleteException(
    val ownerLabel: String,
    val remainingOwnerCount: Int,
    failures: List<Throwable>,
) : IllegalStateException(
    "$ownerLabel close remains incomplete with $remainingOwnerCount native owner(s)",
    failures.firstOrNull(),
) {
    init {
        require(ownerLabel.isNotBlank())
        require(remainingOwnerCount > 0)
        failures.drop(1).forEach(::addSuppressed)
    }
}
