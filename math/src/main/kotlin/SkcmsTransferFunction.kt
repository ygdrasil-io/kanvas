package org.graphiks.math

/**
 * Bit-compatible port of `skcms_TransferFunction`. Same field order as upstream.
 *
 * For an sRGBish transfer function, the encoded → linear evaluation is:
 * ```
 *   y = (a*x + b)^g + e   for x >= d
 *   y = c*x + f           for x <  d
 * ```
 *
 * Other types (PQ, HLG, …) reuse the same 7 floats but interpret them
 * differently — only sRGBish is required for Phase 1.
 */
public data class SkcmsTransferFunction(
    public val g: Float,
    public val a: Float,
    public val b: Float,
    public val c: Float,
    public val d: Float,
    public val e: Float,
    public val f: Float,
)
