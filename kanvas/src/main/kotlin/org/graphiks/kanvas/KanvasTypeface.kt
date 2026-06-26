package org.graphiks.kanvas

/**
 * API-side typeface handle for the Kanvas bridge.
 *
 * Carries only a classpath font-resource path (a TrueType `.ttf`). It lives on
 * the public Kanvas API surface ([TextBlob]) and is resolved during
 * [TextBlob.lower] into a real glyph atlas artifact. It is intentionally NOT
 * referenced by any normalized GPU command: per the text glyph pipeline target
 * (`.upstream/specs/gpu-renderer/21-text-glyph-pipeline.md`), `DrawTextRun` must
 * not carry typefaces or font bytes — only the already-rasterized artifact.
 */
data class KanvasTypeface(val resourcePath: String)
