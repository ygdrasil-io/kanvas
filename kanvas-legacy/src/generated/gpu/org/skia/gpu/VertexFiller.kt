package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import kotlin.UShort
import kotlin.Unit
import org.skia.core.SkPMColor4f
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSpan
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkIRect
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class VertexFiller {
 * public:
 *     VertexFiller(skgpu::MaskFormat maskFormat,
 *                  const SkMatrix &creationMatrix,
 *                  SkRect creationBounds,
 *                  SkSpan<const SkPoint> leftTop,
 *                  bool canDrawDirect);
 *
 *     static VertexFiller Make(skgpu::MaskFormat maskType,
 *                              const SkMatrix &creationMatrix,
 *                              SkRect creationBounds,
 *                              SkSpan<const SkPoint> positions,
 *                              SubRunAllocator *alloc,
 *                              FillerType fillerType);
 *
 *     static std::optional<VertexFiller> MakeFromBuffer(SkReadBuffer &buffer,
 *                                                       SubRunAllocator *alloc);
 *
 *     int unflattenSize() const { return fLeftTop.size_bytes(); }
 *
 *     void flatten(SkWriteBuffer &buffer) const;
 *
 *     // These are only available if the Ganesh backend is compiled in (see GaneshVertexFiller.cpp)
 *     size_t vertexStride(const SkMatrix &matrix) const;
 *     void fillVertexData(int offset, int count,
 *                         SkSpan<const Glyph*> glyphs,
 *                         const SkPMColor4f& color,
 *                         const SkMatrix& positionMatrix,
 *                         SkIRect clip,
 *                         void* vertexBuffer) const;
 *
 *     // This is only available if the Graphite backend is compiled in (see GraphiteVertexFiller.cpp)
 *     void fillInstanceData(skgpu::graphite::DrawWriter* dw,
 *                           int offset, int count,
 *                           unsigned short flags,
 *                           uint32_t ssboIndex,
 *                           SkSpan<const Glyph*> glyphs,
 *                           SkScalar depth) const;
 *
 *     std::tuple<skgpu::graphite::Rect, skgpu::graphite::Transform> boundsAndDeviceMatrix(
 *             const skgpu::graphite::Transform& localToDevice, SkPoint drawOrigin) const;
 *
 *     // Return true if the positionMatrix represents an integer translation. Return the device
 *     // bounding box of all the glyphs. If the bounding box is empty, then something went singular
 *     // and this operation should be dropped.
 *     std::tuple<bool, SkRect> deviceRectAndCheckTransform(const SkMatrix &positionMatrix) const;
 *
 *     skgpu::MaskFormat grMaskType() const { return fMaskType; }
 *     bool isLCD() const;
 *
 *     int count() const { return SkCount(fLeftTop); }
 *
 * private:
 *     static std::tuple<bool, SkVector> CanUseDirect(const SkMatrix& creationMatrix,
 *                                                    const SkMatrix& positionMatrix);
 *
 *     SkMatrix viewDifference(const SkMatrix &positionMatrix) const;
 *
 *     const skgpu::MaskFormat fMaskType;
 *     const bool fCanDrawDirect;
 *     const SkMatrix fCreationMatrix;
 *     const SkRect fCreationBounds;
 *     const SkSpan<const SkPoint> fLeftTop;
 * }
 * ```
 */
public data class VertexFiller public constructor(
  /**
   * C++ original:
   * ```cpp
   * const skgpu::MaskFormat fMaskType
   * ```
   */
  private val fMaskType: MaskFormat,
  /**
   * C++ original:
   * ```cpp
   * const bool fCanDrawDirect
   * ```
   */
  private val fCanDrawDirect: Boolean,
  /**
   * C++ original:
   * ```cpp
   * const SkMatrix fCreationMatrix
   * ```
   */
  private val fCreationMatrix: Int,
  /**
   * C++ original:
   * ```cpp
   * const SkRect fCreationBounds
   * ```
   */
  private val fCreationBounds: Int,
  /**
   * C++ original:
   * ```cpp
   * const SkSpan<const SkPoint> fLeftTop
   * ```
   */
  private val fLeftTop: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * int unflattenSize() const { return fLeftTop.size_bytes(); }
   * ```
   */
  public fun unflattenSize(): Int {
    TODO("Implement unflattenSize")
  }

  /**
   * C++ original:
   * ```cpp
   * void VertexFiller::flatten(SkWriteBuffer &buffer) const {
   *     buffer.writeInt(static_cast<int>(fMaskType));
   *     buffer.writeBool(fCanDrawDirect);
   *     buffer.writeMatrix(fCreationMatrix);
   *     buffer.writeRect(fCreationBounds);
   *     buffer.writePointArray(fLeftTop);
   * }
   * ```
   */
  public fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t vertexStride(const SkMatrix &matrix) const
   * ```
   */
  public fun vertexStride(matrix: SkMatrix): Int {
    TODO("Implement vertexStride")
  }

  /**
   * C++ original:
   * ```cpp
   * void fillVertexData(int offset, int count,
   *                         SkSpan<const Glyph*> glyphs,
   *                         const SkPMColor4f& color,
   *                         const SkMatrix& positionMatrix,
   *                         SkIRect clip,
   *                         void* vertexBuffer) const
   * ```
   */
  public fun fillVertexData(
    offset: Int,
    count: Int,
    glyphs: SkSpan<Glyph?>,
    color: SkPMColor4f,
    positionMatrix: SkMatrix,
    clip: SkIRect,
    vertexBuffer: Unit?,
  ) {
    TODO("Implement fillVertexData")
  }

  /**
   * C++ original:
   * ```cpp
   * void VertexFiller::fillInstanceData(skgpu::graphite::DrawWriter* dw,
   *                                     int offset, int count,
   *                                     unsigned short flags,
   *                                     uint32_t ssboIndex,
   *                                     SkSpan<const Glyph*> glyphs,
   *                                     SkScalar depth) const {
   *     auto quadData = [&]() {
   *         return SkMakeZip(glyphs.subspan(offset, count),
   *                          fLeftTop.subspan(offset, count));
   *     };
   *
   *     skgpu::graphite::DrawWriter::Instances instances{*dw, {}, {}, 4};
   *     instances.reserve(count);
   *     // Need to send width, height, uvPos, xyPos, and strikeToSourceScale
   *     // pre-transform coords = (s*w*b_x + t_x, s*h*b_y + t_y)
   *     // where (b_x, b_y) are the vertexID coords
   *     for (auto [glyph, leftTop]: quadData()) {
   *         auto[al, at, ar, ab] = glyph->fAtlasLocator.getUVs();
   *         instances.append(1) << AtlasPt{uint16_t(ar-al), uint16_t(ab-at)}
   *                             << AtlasPt{uint16_t(al & 0x1fff), at}
   *                             << leftTop << /*index=*/uint16_t(al >> 13) << flags
   *                             << 1.0f
   *                             << depth << ssboIndex;
   *     }
   * }
   * ```
   */
  public fun fillInstanceData(
    dw: DrawWriter?,
    offset: Int,
    count: Int,
    flags: UShort,
    ssboIndex: UInt,
    glyphs: SkSpan<Glyph?>,
    depth: SkScalar,
  ) {
    TODO("Implement fillInstanceData")
  }

  /**
   * C++ original:
   * ```cpp
   * std::tuple<Rect, Transform> VertexFiller::boundsAndDeviceMatrix(const Transform& localToDevice,
   *                                                                 SkPoint drawOrigin) const {
   *     // The baked-in matrix differs from the current localToDevice by a translation if the
   *     // upper 2x2 remains the same, and there's no perspective. Since there's no projection,
   *     // Z is irrelevant, so it's okay that fCreationMatrix is an SkMatrix and has
   *     // discarded the 3rd row/col, and can ignore those values in localToDevice.
   *     const SkM44& positionMatrix = localToDevice.matrix();
   *     const bool compatibleMatrix = positionMatrix.rc(0,0) == fCreationMatrix.rc(0, 0) &&
   *                                   positionMatrix.rc(0,1) == fCreationMatrix.rc(0, 1) &&
   *                                   positionMatrix.rc(1,0) == fCreationMatrix.rc(1, 0) &&
   *                                   positionMatrix.rc(1,1) == fCreationMatrix.rc(1, 1) &&
   *                                   localToDevice.type() != Transform::Type::kPerspective &&
   *                                   !fCreationMatrix.hasPerspective();
   *
   *     if (compatibleMatrix) {
   *         const SkV4 mappedOrigin = positionMatrix.map(drawOrigin.x(), drawOrigin.y(), 0.f, 1.f);
   *         const SkV2 offset = {mappedOrigin.x - fCreationMatrix.getTranslateX(),
   *                              mappedOrigin.y - fCreationMatrix.getTranslateY()};
   *         if (SkScalarIsInt(offset.x) && SkScalarIsInt(offset.y)) {
   *             // The offset is an integer (but make sure), which means the generated mask can be
   *             // accessed without changing how texels would be sampled.
   *             return {Rect(fCreationBounds),
   *                     Transform(SkM44::Translate(SkScalarRoundToInt(offset.x),
   *                                                SkScalarRoundToInt(offset.y)))};
   *         }
   *     }
   *
   *     // Otherwise compute the relative transformation from fCreationMatrix to
   *     // localToDevice, with the drawOrigin applied. If fCreationMatrix or the
   *     // concatenation is not invertible the returned Transform is marked invalid and the draw
   *     // will be automatically dropped.
   *     const SkMatrix viewDifference = this->viewDifference(
   *             localToDevice.preTranslate(drawOrigin.x(), drawOrigin.y()));
   *     return {Rect(fCreationBounds), Transform(SkM44(viewDifference))};
   * }
   * ```
   */
  public fun boundsAndDeviceMatrix(localToDevice: Transform, drawOrigin: SkPoint): Int {
    TODO("Implement boundsAndDeviceMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * std::tuple<bool, SkRect> VertexFiller::deviceRectAndCheckTransform(
   *             const SkMatrix &positionMatrix) const {
   *     if (fCanDrawDirect) {
   *         const auto [directDrawCompatible, offset] = CanUseDirect(fCreationMatrix, positionMatrix);
   *
   *         if (directDrawCompatible) {
   *             return {true, fCreationBounds.makeOffset(offset)};
   *         }
   *     }
   *
   *     if (SkMatrix inverse; fCreationMatrix.invert(&inverse)) {
   *         SkMatrix viewDifference = SkMatrix::Concat(positionMatrix, inverse);
   *         return {false, viewDifference.mapRect(fCreationBounds)};
   *     }
   *
   *     // initialPositionMatrix is singular. Do nothing.
   *     return {false, SkRect::MakeEmpty()};
   * }
   * ```
   */
  public fun deviceRectAndCheckTransform(positionMatrix: SkMatrix): Int {
    TODO("Implement deviceRectAndCheckTransform")
  }

  /**
   * C++ original:
   * ```cpp
   * skgpu::MaskFormat grMaskType() const { return fMaskType; }
   * ```
   */
  public fun grMaskType(): MaskFormat {
    TODO("Implement grMaskType")
  }

  /**
   * C++ original:
   * ```cpp
   * bool VertexFiller::isLCD() const { return fMaskType == MaskFormat::kA565; }
   * ```
   */
  public fun isLCD(): Boolean {
    TODO("Implement isLCD")
  }

  /**
   * C++ original:
   * ```cpp
   * int count() const { return SkCount(fLeftTop); }
   * ```
   */
  public fun count(): Int {
    TODO("Implement count")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix VertexFiller::viewDifference(const SkMatrix &positionMatrix) const {
   *     if (SkMatrix inverse; fCreationMatrix.invert(&inverse)) {
   *         return SkMatrix::Concat(positionMatrix, inverse);
   *     }
   *     return SkMatrix::I();
   * }
   * ```
   */
  private fun viewDifference(positionMatrix: SkMatrix): Int {
    TODO("Implement viewDifference")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * VertexFiller VertexFiller::Make(MaskFormat maskType,
     *                                 const SkMatrix &creationMatrix,
     *                                 SkRect creationBounds,
     *                                 SkSpan<const SkPoint> positions,
     *                                 SubRunAllocator *alloc,
     *                                 FillerType fillerType) {
     *     SkSpan<SkPoint> leftTop = alloc->makePODSpan<SkPoint>(positions);
     *     return VertexFiller{
     *             maskType, creationMatrix, creationBounds, leftTop, fillerType == kIsDirect};
     * }
     * ```
     */
    public fun make(
      maskType: MaskFormat,
      creationMatrix: SkMatrix,
      creationBounds: SkRect,
      positions: SkSpan<SkPoint>,
      alloc: SubRunAllocator?,
      fillerType: FillerType,
    ): VertexFiller {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * std::optional<VertexFiller> VertexFiller::MakeFromBuffer(SkReadBuffer &buffer,
     *                                                          SubRunAllocator *alloc) {
     *     int checkingMaskType = buffer.readInt();
     *     if (!buffer.validate(
     *             0 <= checkingMaskType && checkingMaskType < skgpu::kMaskFormatCount)) {
     *         return std::nullopt;
     *     }
     *     MaskFormat maskType = (MaskFormat) checkingMaskType;
     *
     *     const bool canDrawDirect = buffer.readBool();
     *
     *     SkMatrix creationMatrix;
     *     buffer.readMatrix(&creationMatrix);
     *
     *     SkRect creationBounds = buffer.readRect();
     *
     *     SkSpan<SkPoint> leftTop = MakePointsFromBuffer(buffer, alloc);
     *     if (leftTop.empty()) { return std::nullopt; }
     *
     *     SkASSERT(buffer.isValid());
     *     return VertexFiller{maskType, creationMatrix, creationBounds, leftTop, canDrawDirect};
     * }
     * ```
     */
    public fun makeFromBuffer(buffer: SkReadBuffer, alloc: SubRunAllocator?): Int {
      TODO("Implement makeFromBuffer")
    }

    /**
     * C++ original:
     * ```cpp
     * std::tuple<bool, SkVector> VertexFiller::CanUseDirect(
     *         const SkMatrix& creationMatrix, const SkMatrix& positionMatrix) {
     *     // The existing direct glyph info can be used if the creationMatrix, and the
     *     // positionMatrix have the same 2x2, the translation between them is integer, and no
     *     // perspective is involved. Calculate the translation in source space to a translation in
     *     // device space by mapping (0, 0) through both the creationMatrix and the positionMatrix;
     *     // take the difference.
     *     SkVector translation = positionMatrix.mapOrigin() - creationMatrix.mapOrigin();
     *     return {creationMatrix.getScaleX() == positionMatrix.getScaleX() &&
     *             creationMatrix.getScaleY() == positionMatrix.getScaleY() &&
     *             creationMatrix.getSkewX()  == positionMatrix.getSkewX()  &&
     *             creationMatrix.getSkewY()  == positionMatrix.getSkewY()  &&
     *             !positionMatrix.hasPerspective() && !creationMatrix.hasPerspective() &&
     *             SkScalarIsInt(translation.x()) && SkScalarIsInt(translation.y()),
     *             translation};
     * }
     * ```
     */
    private fun canUseDirect(creationMatrix: SkMatrix, positionMatrix: SkMatrix): Int {
      TODO("Implement canUseDirect")
    }
  }
}
