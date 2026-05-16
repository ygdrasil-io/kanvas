package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import kotlin.Unit
import org.skia.core.SkPMColor4f
import org.skia.math.SkIRect
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import undefined.RegenerateAtlasDelegate

public typealias AtlasSubRun = AtlasSubRun

/**
 * C++ original:
 * ```cpp
 * class AtlasSubRun : public SubRun {
 * public:
 *     AtlasSubRun(VertexFiller&& vertexFiller, GlyphVector&& glyphs)
 *             : fVertexFiller{std::move(vertexFiller)}
 *             , fGlyphs{std::move(glyphs)} {}
 *     ~AtlasSubRun() override = default;
 *
 *     SkSpan<const Glyph*> glyphs() const { return fGlyphs.glyphs(); }
 *     int glyphCount() const { return SkCount(fGlyphs.glyphs()); }
 *     skgpu::MaskFormat maskFormat() const { return fVertexFiller.grMaskType(); }
 *     virtual int glyphSrcPadding() const = 0;
 *     unsigned short instanceFlags() const { return (unsigned short)this->maskFormat(); }
 *
 *     virtual std::tuple<bool, SkRect> deviceRectAndNeedsTransform(
 *             const SkMatrix &positionMatrix) const = 0;
 *
 *     struct GlyphParams {
 *         bool isSDF;
 *         bool isLCD;
 *         bool isAA;
 *     };
 *     virtual GlyphParams glyphParams() const = 0;
 *
 *     size_t vertexStride(const SkMatrix& drawMatrix) const {
 *         return fVertexFiller.vertexStride(drawMatrix);
 *     }
 *
 *     void fillVertexData(
 *             void* vertexDst, int offset, int count,
 *             const SkPMColor4f& color,
 *             const SkMatrix& drawMatrix,
 *             SkPoint drawOrigin,
 *             SkIRect clip) const {
 *         SkMatrix positionMatrix = drawMatrix;
 *         positionMatrix.preTranslate(drawOrigin.x(), drawOrigin.y());
 *         fVertexFiller.fillVertexData(offset, count,
 *                                      fGlyphs.glyphs(),
 *                                      color,
 *                                      positionMatrix,
 *                                      clip,
 *                                      vertexDst);
 *     }
 *
 *     // This call is not thread safe. It should only be called from a known single-threaded env.
 *     virtual std::tuple<bool, int> regenerateAtlas(
 *             int begin, int end, RegenerateAtlasDelegate) const = 0;
 *
 *     const VertexFiller& vertexFiller() const { return fVertexFiller; }
 *
 *     virtual void testingOnly_packedGlyphIDToGlyph(StrikeCache* cache) const = 0;
 *
 * protected:
 *     const VertexFiller fVertexFiller;
 *
 *     // The regenerateAtlas method mutates fGlyphs. It should be called from onPrepare which must
 *     // be single threaded.
 *     mutable GlyphVector fGlyphs;
 * }
 * ```
 */
public abstract class AtlasSubRun public constructor(
  vertexFiller: VertexFiller,
  glyphs: GlyphVector,
) : SubRun() {
  /**
   * C++ original:
   * ```cpp
   * const VertexFiller fVertexFiller
   * ```
   */
  protected val fVertexFiller: Int = TODO("Initialize fVertexFiller")

  /**
   * C++ original:
   * ```cpp
   * mutable GlyphVector fGlyphs
   * ```
   */
  protected var fGlyphs: Int = TODO("Initialize fGlyphs")

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const Glyph*> glyphs() const { return fGlyphs.glyphs(); }
   * ```
   */
  public fun glyphs(): Int {
    TODO("Implement glyphs")
  }

  /**
   * C++ original:
   * ```cpp
   * int glyphCount() const { return SkCount(fGlyphs.glyphs()); }
   * ```
   */
  public fun glyphCount(): Int {
    TODO("Implement glyphCount")
  }

  /**
   * C++ original:
   * ```cpp
   * skgpu::MaskFormat maskFormat() const { return fVertexFiller.grMaskType(); }
   * ```
   */
  public fun maskFormat(): MaskFormat {
    TODO("Implement maskFormat")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual int glyphSrcPadding() const = 0
   * ```
   */
  public abstract fun glyphSrcPadding(): Int

  /**
   * C++ original:
   * ```cpp
   * unsigned short instanceFlags() const { return (unsigned short)this->maskFormat(); }
   * ```
   */
  public fun instanceFlags(): UInt {
    TODO("Implement instanceFlags")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual std::tuple<bool, SkRect> deviceRectAndNeedsTransform(
   *             const SkMatrix &positionMatrix) const = 0
   * ```
   */
  public abstract fun deviceRectAndNeedsTransform(positionMatrix: SkMatrix): Int

  /**
   * C++ original:
   * ```cpp
   * virtual GlyphParams glyphParams() const = 0
   * ```
   */
  public abstract fun glyphParams(): GlyphParams

  /**
   * C++ original:
   * ```cpp
   * size_t vertexStride(const SkMatrix& drawMatrix) const {
   *         return fVertexFiller.vertexStride(drawMatrix);
   *     }
   * ```
   */
  public fun vertexStride(drawMatrix: SkMatrix): Int {
    TODO("Implement vertexStride")
  }

  /**
   * C++ original:
   * ```cpp
   * void fillVertexData(
   *             void* vertexDst, int offset, int count,
   *             const SkPMColor4f& color,
   *             const SkMatrix& drawMatrix,
   *             SkPoint drawOrigin,
   *             SkIRect clip) const {
   *         SkMatrix positionMatrix = drawMatrix;
   *         positionMatrix.preTranslate(drawOrigin.x(), drawOrigin.y());
   *         fVertexFiller.fillVertexData(offset, count,
   *                                      fGlyphs.glyphs(),
   *                                      color,
   *                                      positionMatrix,
   *                                      clip,
   *                                      vertexDst);
   *     }
   * ```
   */
  public fun fillVertexData(
    vertexDst: Unit?,
    offset: Int,
    count: Int,
    color: SkPMColor4f,
    drawMatrix: SkMatrix,
    drawOrigin: SkPoint,
    clip: SkIRect,
  ) {
    TODO("Implement fillVertexData")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual std::tuple<bool, int> regenerateAtlas(
   *             int begin, int end, RegenerateAtlasDelegate) const = 0
   * ```
   */
  public abstract fun regenerateAtlas(
    begin: Int,
    end: Int,
    param2: RegenerateAtlasDelegate,
  ): Int

  /**
   * C++ original:
   * ```cpp
   * const VertexFiller& vertexFiller() const { return fVertexFiller; }
   * ```
   */
  public fun vertexFiller(): Int {
    TODO("Implement vertexFiller")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void testingOnly_packedGlyphIDToGlyph(StrikeCache* cache) const = 0
   * ```
   */
  public abstract fun testingOnlyPackedGlyphIDToGlyph(cache: StrikeCache?)

  public data class GlyphParams public constructor(
    public var isSDF: Boolean,
    public var isLCD: Boolean,
    public var isAA: Boolean,
  )
}
