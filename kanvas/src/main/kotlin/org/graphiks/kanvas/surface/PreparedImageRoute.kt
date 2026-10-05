package org.graphiks.kanvas.surface

/**
 * Public image-preparation routes accepted by a [Surface].
 *
 * The names describe the admitted image behavior without exposing the
 * implementation capability used by a concrete renderer.
 */
enum class PreparedImageRoute {
    GENERIC_NATIVE,
    BOUNDED_NEAREST_1_TO_1,
}
