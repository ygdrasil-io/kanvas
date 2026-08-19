@file:Suppress("DEPRECATION")

package org.graphiks.kanvas.gpu.renderer.images

/**
 * Source-compatible image-package alias.
 *
 * New renderer code must import the canonical artifact contract from `artifacts`.
 */
@Deprecated(
    message = "Import GPUImageUploadArtifactKey from the artifacts package.",
    replaceWith = ReplaceWith(
        expression = "GPUImageUploadArtifactKey",
        imports = ["org.graphiks.kanvas.gpu.renderer.artifacts.GPUImageUploadArtifactKey"],
    ),
)
typealias GPUImageUploadArtifactKey =
    org.graphiks.kanvas.gpu.renderer.artifacts.GPUImageUploadArtifactKey

/**
 * Source-compatible image-package alias.
 *
 * New renderer code must import the canonical artifact contract from `artifacts`.
 */
@Deprecated(
    message = "Import GPUPreparedImagePixelLayout from the artifacts package.",
    replaceWith = ReplaceWith(
        expression = "GPUPreparedImagePixelLayout",
        imports = ["org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedImagePixelLayout"],
    ),
)
typealias GPUPreparedImagePixelLayout =
    org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedImagePixelLayout

/**
 * Source-compatible image-package alias.
 *
 * New renderer code must import the canonical artifact contract from `artifacts`.
 */
@Deprecated(
    message = "Import GPUPreparedImageUploadArtifact from the artifacts package.",
    replaceWith = ReplaceWith(
        expression = "GPUPreparedImageUploadArtifact",
        imports = ["org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedImageUploadArtifact"],
    ),
)
typealias GPUPreparedImageUploadArtifact =
    org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedImageUploadArtifact

/** Internal source-compatible alias for the canonical artifact upload encoding. */
@Deprecated(
    message = "Import GPUPreparedColorUploadEncoding from the artifacts package.",
    replaceWith = ReplaceWith(
        expression = "GPUPreparedColorUploadEncoding",
        imports = ["org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedColorUploadEncoding"],
    ),
)
internal typealias GPUPreparedColorUploadEncoding =
    org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedColorUploadEncoding

/** Internal source-compatible alias for the canonical prepared SDR contract. */
@Deprecated(
    message = "Import GPUPreparedSdrColorContract from the artifacts package.",
    replaceWith = ReplaceWith(
        expression = "GPUPreparedSdrColorContract",
        imports = ["org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedSdrColorContract"],
    ),
)
internal typealias GPUPreparedSdrColorContract =
    org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedSdrColorContract

/** Internal source-compatible forwarding function for the canonical prepared SDR contract. */
@Deprecated(
    message = "Import preparedSdrColorContract from the artifacts package.",
    replaceWith = ReplaceWith(
        expression = "preparedSdrColorContract()",
        imports = ["org.graphiks.kanvas.gpu.renderer.artifacts.preparedSdrColorContract"],
    ),
)
internal fun preparedSdrColorContract(): GPUPreparedSdrColorContract =
    org.graphiks.kanvas.gpu.renderer.artifacts.preparedSdrColorContract()
