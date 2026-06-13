package org.graphiks.kanvas.gpu.renderer.text

/** GPU text run plan after pure Kotlin text shaping and layout. */
class GPUTextRunPlan

/** Subrun plan split by glyph representation, transform, and atlas facts. */
class GPUTextSubRunPlan

/** Route selected for a text or glyph subrun. */
class GPUTextRoute

/** Render-step contract for text atlas or glyph rendering. */
class GPUTextRenderStep

/** Text atlas selection and upload plan. */
class GPUTextAtlasPlan

/** Binding contract for text atlas and instance data. */
class GPUTextBinding

/** Instance buffer layout plan for text rendering. */
class GPUTextInstancePlan

/** Signed distance field parameters for text rendering. */
class GPUTextSDFParams

/** Ordering token for text upload and draw dependencies. */
class GPUTextOrderingToken

/** Typed glyph atlas artifact for A8 coverage glyphs. */
class GlyphAtlasArtifact

/** Typed signed-distance-field glyph atlas artifact. */
class SDFGlyphAtlasArtifact

/** Glyph upload plan consumed by text atlas materialization. */
class GlyphUploadPlan

/** Outline glyph route plan. */
class OutlineGlyphPlan

/** Color glyph route plan. */
class ColorGlyphPlan

/** Bitmap glyph route plan. */
class BitmapGlyphPlan

/** SVG glyph route plan. */
class SVGGlyphPlan

/** Diagnostic emitted by text route planning. */
class GPUTextDiagnostic
