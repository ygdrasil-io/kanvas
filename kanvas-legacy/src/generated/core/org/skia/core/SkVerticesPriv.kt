package org.skia.core

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkColor
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * class SkVerticesPriv {
 * public:
 *     SkVertices::VertexMode mode() const { return fVertices->fMode; }
 *
 *     bool hasColors() const { return SkToBool(fVertices->fColors); }
 *     bool hasTexCoords() const { return SkToBool(fVertices->fTexs); }
 *     bool hasIndices() const { return SkToBool(fVertices->fIndices); }
 *
 *     int vertexCount() const { return fVertices->fVertexCount; }
 *     int indexCount() const { return fVertices->fIndexCount; }
 *
 *     const SkPoint* positions() const { return fVertices->fPositions; }
 *     const SkPoint* texCoords() const { return fVertices->fTexs; }
 *     const SkColor* colors() const { return fVertices->fColors; }
 *     const uint16_t* indices() const { return fVertices->fIndices; }
 *
 *     // Never called due to RVO in priv(), but must exist for MSVC 2017.
 *     SkVerticesPriv(const SkVerticesPriv&) = default;
 *
 *     void encode(SkWriteBuffer&) const;
 *     static sk_sp<SkVertices> Decode(SkReadBuffer&);
 *
 * private:
 *     explicit SkVerticesPriv(SkVertices* vertices) : fVertices(vertices) {}
 *     SkVerticesPriv& operator=(const SkVerticesPriv&) = delete;
 *
 *     // No taking addresses of this type
 *     const SkVerticesPriv* operator&() const = delete;
 *     SkVerticesPriv* operator&() = delete;
 *
 *     SkVertices* fVertices;
 *
 *     friend class SkVertices; // to construct this type
 * }
 * ```
 */
public data class SkVerticesPriv public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkVertices* fVertices
   * ```
   */
  private var fVertices: SkVertices?,
) {
  /**
   * C++ original:
   * ```cpp
   * SkVertices::VertexMode mode() const { return fVertices->fMode; }
   * ```
   */
  public fun mode(): SkVertices.VertexMode {
    TODO("Implement mode")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasColors() const { return SkToBool(fVertices->fColors); }
   * ```
   */
  public fun hasColors(): Boolean {
    TODO("Implement hasColors")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasTexCoords() const { return SkToBool(fVertices->fTexs); }
   * ```
   */
  public fun hasTexCoords(): Boolean {
    TODO("Implement hasTexCoords")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasIndices() const { return SkToBool(fVertices->fIndices); }
   * ```
   */
  public fun hasIndices(): Boolean {
    TODO("Implement hasIndices")
  }

  /**
   * C++ original:
   * ```cpp
   * int vertexCount() const { return fVertices->fVertexCount; }
   * ```
   */
  public fun vertexCount(): Int {
    TODO("Implement vertexCount")
  }

  /**
   * C++ original:
   * ```cpp
   * int indexCount() const { return fVertices->fIndexCount; }
   * ```
   */
  public fun indexCount(): Int {
    TODO("Implement indexCount")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkPoint* positions() const { return fVertices->fPositions; }
   * ```
   */
  public fun positions(): SkPoint {
    TODO("Implement positions")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkPoint* texCoords() const { return fVertices->fTexs; }
   * ```
   */
  public fun texCoords(): SkPoint {
    TODO("Implement texCoords")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkColor* colors() const { return fVertices->fColors; }
   * ```
   */
  public fun colors(): SkColor {
    TODO("Implement colors")
  }

  /**
   * C++ original:
   * ```cpp
   * const uint16_t* indices() const { return fVertices->fIndices; }
   * ```
   */
  public fun indices(): Int {
    TODO("Implement indices")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkVerticesPriv::encode(SkWriteBuffer& buffer) const {
   *     // packed has room for additional flags in the future
   *     uint32_t packed = static_cast<uint32_t>(fVertices->fMode);
   *     SkASSERT((packed & ~kMode_Mask) == 0);  // our mode fits in the mask bits
   *     if (fVertices->fTexs) {
   *         packed |= kHasTexs_Mask;
   *     }
   *     if (fVertices->fColors) {
   *         packed |= kHasColors_Mask;
   *     }
   *
   *     SkVertices::Sizes sizes = fVertices->getSizes();
   *     SkASSERT(!sizes.fBuilderTriFanISize);
   *
   *     // Header
   *     buffer.writeUInt(packed);
   *     buffer.writeInt(fVertices->fVertexCount);
   *     buffer.writeInt(fVertices->fIndexCount);
   *
   *     // Data arrays
   *     buffer.writeByteArray(fVertices->fPositions, sizes.fVSize);
   *     buffer.writeByteArray(fVertices->fTexs, sizes.fTSize);
   *     buffer.writeByteArray(fVertices->fColors, sizes.fCSize);
   *     // if index-count is odd, we won't be 4-bytes aligned, so we call the pad version
   *     buffer.writeByteArray(fVertices->fIndices, sizes.fISize);
   * }
   * ```
   */
  public fun encode(buffer: SkWriteBuffer) {
    TODO("Implement encode")
  }

  /**
   * C++ original:
   * ```cpp
   * SkVerticesPriv& operator=(const SkVerticesPriv&) = delete
   * ```
   */
  private fun assign(param0: SkVerticesPriv) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkVerticesPriv* operator&() const = delete
   * ```
   */
  private fun addressOf(): SkVerticesPriv {
    TODO("Implement addressOf")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkVertices> SkVerticesPriv::Decode(SkReadBuffer& buffer) {
     *     auto decode = [](SkReadBuffer& buffer) -> sk_sp<SkVertices> {
     *         SkSafeRange safe;
     *         bool hasCustomData = buffer.isVersionLT(SkPicturePriv::kVerticesRemoveCustomData_Version);
     *
     *         const uint32_t packed = buffer.readUInt();
     *         const int vertexCount = safe.checkGE(buffer.readInt(), 0);
     *         const int indexCount = safe.checkGE(buffer.readInt(), 0);
     *         const int attrCount = hasCustomData ? safe.checkGE(buffer.readInt(), 0) : 0;
     *         const SkVertices::VertexMode mode = safe.checkLE<SkVertices::VertexMode>(
     *                 packed & kMode_Mask, SkVertices::kLast_VertexMode);
     *         const bool hasTexs = SkToBool(packed & kHasTexs_Mask);
     *         const bool hasColors = SkToBool(packed & kHasColors_Mask);
     *
     *         // Check that the header fields and buffer are valid. If this is data with the experimental
     *         // custom attributes feature - we don't support that any more.
     *         // We also don't support serialized triangle-fan data. We stopped writing that long ago,
     *         // so it should never appear in valid encoded data.
     *         if (!safe || !buffer.isValid() || attrCount ||
     *             mode == SkVertices::kTriangleFan_VertexMode) {
     *             return nullptr;
     *         }
     *
     *         const SkVertices::Desc desc{mode, vertexCount, indexCount, hasTexs, hasColors};
     *         SkVertices::Sizes sizes(desc);
     *         if (!sizes.isValid() || sizes.fArrays > buffer.available()) {
     *             return nullptr;
     *         }
     *
     *         SkVertices::Builder builder(desc);
     *         if (!builder.isValid()) {
     *             return nullptr;
     *         }
     *
     *         buffer.readByteArray(builder.positions(), sizes.fVSize);
     *         if (hasCustomData) {
     *             size_t customDataSize = 0;
     *             buffer.skipByteArray(&customDataSize);
     *             if (customDataSize != 0) {
     *                 return nullptr;
     *             }
     *         }
     *         buffer.readByteArray(builder.texCoords(), sizes.fTSize);
     *         buffer.readByteArray(builder.colors(), sizes.fCSize);
     *         buffer.readByteArray(builder.indices(), sizes.fISize);
     *
     *         if (!buffer.isValid()) {
     *             return nullptr;
     *         }
     *
     *         if (indexCount > 0) {
     *             // validate that the indices are in range
     *             const uint16_t* indices = builder.indices();
     *             for (int i = 0; i < indexCount; ++i) {
     *                 if (indices[i] >= (unsigned)vertexCount) {
     *                     return nullptr;
     *                 }
     *             }
     *         }
     *
     *         return builder.detach();
     *     };
     *
     *     if (auto verts = decode(buffer)) {
     *         return verts;
     *     }
     *     buffer.validate(false);
     *     return nullptr;
     * }
     * ```
     */
    public fun decode(buffer: SkReadBuffer): SkSp<SkVertices> {
      TODO("Implement decode")
    }
  }
}
