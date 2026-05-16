package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.UByte
import kotlin.UInt
import kotlin.ULong
import kotlin.UShort
import kotlin.Unit
import org.skia.foundation.SkColor
import org.skia.foundation.SkNVRefCnt
import org.skia.math.SkPoint
import undefined.Desc
import undefined.Sizes

/**
 * C++ original:
 * ```cpp
 * class SK_API SkVertices : public SkNVRefCnt<SkVertices> {
 *     struct Desc;
 *     struct Sizes;
 * public:
 *     enum VertexMode {
 *         kTriangles_VertexMode,
 *         kTriangleStrip_VertexMode,
 *         kTriangleFan_VertexMode,
 *
 *         kLast_VertexMode = kTriangleFan_VertexMode,
 *     };
 *
 *     /**
 *      *  Create a vertices by copying the specified arrays. texs, colors may be nullptr,
 *      *  and indices is ignored if indexCount == 0.
 *      */
 *     static sk_sp<SkVertices> MakeCopy(VertexMode mode, int vertexCount,
 *                                       const SkPoint positions[],
 *                                       const SkPoint texs[],
 *                                       const SkColor colors[],
 *                                       int indexCount,
 *                                       const uint16_t indices[]);
 *
 *     static sk_sp<SkVertices> MakeCopy(VertexMode mode, int vertexCount,
 *                                       const SkPoint positions[],
 *                                       const SkPoint texs[],
 *                                       const SkColor colors[]) {
 *         return MakeCopy(mode,
 *                         vertexCount,
 *                         positions,
 *                         texs,
 *                         colors,
 *                         0,
 *                         nullptr);
 *     }
 *
 *     enum BuilderFlags {
 *         kHasTexCoords_BuilderFlag   = 1 << 0,
 *         kHasColors_BuilderFlag      = 1 << 1,
 *     };
 *     class SK_API Builder {
 *     public:
 *         Builder(VertexMode mode, int vertexCount, int indexCount, uint32_t flags);
 *
 *         bool isValid() const { return fVertices != nullptr; }
 *
 *         SkPoint* positions();
 *         uint16_t* indices();        // returns null if there are no indices
 *
 *         // If we have custom attributes, these will always be null
 *         SkPoint* texCoords();       // returns null if there are no texCoords
 *         SkColor* colors();          // returns null if there are no colors
 *
 *         // Detach the built vertices object. After the first call, this will always return null.
 *         sk_sp<SkVertices> detach();
 *
 *     private:
 *         Builder(const Desc&);
 *
 *         void init(const Desc&);
 *
 *         // holds a partially complete object. only completed in detach()
 *         sk_sp<SkVertices> fVertices;
 *         // Extra storage for intermediate vertices in the case where the client specifies indexed
 *         // triangle fans. These get converted to indexed triangles when the Builder is finalized.
 *         std::unique_ptr<uint8_t[]> fIntermediateFanIndices;
 *
 *         friend class SkVertices;
 *         friend class SkVerticesPriv;
 *     };
 *
 *     uint32_t uniqueID() const { return fUniqueID; }
 *     const SkRect& bounds() const { return fBounds; }
 *
 *     // returns approximate byte size of the vertices object
 *     size_t approximateSize() const;
 *
 *     // Provides access to functions that aren't part of the public API.
 *     SkVerticesPriv priv();
 *     const SkVerticesPriv priv() const;  // NOLINT(readability-const-return-type)
 *
 * private:
 *     SkVertices() {}
 *
 *     friend class SkVerticesPriv;
 *
 *     // these are needed since we've manually sized our allocation (see Builder::init)
 *     friend class SkNVRefCnt<SkVertices>;
 *     void operator delete(void* p);
 *
 *     Sizes getSizes() const;
 *
 *     // we store this first, to pair with the refcnt in our base-class, so we don't have an
 *     // unnecessary pad between it and the (possibly 8-byte aligned) ptrs.
 *     uint32_t fUniqueID;
 *
 *     // these point inside our allocation, so none of these can be "freed"
 *     SkPoint*     fPositions;        // [vertexCount]
 *     uint16_t*    fIndices;          // [indexCount] or null
 *     SkPoint*     fTexs;             // [vertexCount] or null
 *     SkColor*     fColors;           // [vertexCount] or null
 *
 *     SkRect  fBounds;    // computed to be the union of the fPositions[]
 *     int     fVertexCount;
 *     int     fIndexCount;
 *
 *     VertexMode fMode;
 *     // below here is where the actual array data is stored.
 * }
 * ```
 */
public open class SkVertices public constructor() : SkNVRefCnt(), SkVertices {
  /**
   * C++ original:
   * ```cpp
   * uint32_t fUniqueID
   * ```
   */
  private var fUniqueID: UInt = TODO("Initialize fUniqueID")

  /**
   * C++ original:
   * ```cpp
   * SkPoint*     fPositions
   * ```
   */
  private var fPositions: Int? = TODO("Initialize fPositions")

  /**
   * C++ original:
   * ```cpp
   * uint16_t*    fIndices
   * ```
   */
  private var fIndices: UShort? = TODO("Initialize fIndices")

  /**
   * C++ original:
   * ```cpp
   * SkPoint*     fTexs
   * ```
   */
  private var fTexs: Int? = TODO("Initialize fTexs")

  /**
   * C++ original:
   * ```cpp
   * SkColor*     fColors
   * ```
   */
  private var fColors: Int? = TODO("Initialize fColors")

  /**
   * C++ original:
   * ```cpp
   * SkRect  fBounds
   * ```
   */
  private var fBounds: Int = TODO("Initialize fBounds")

  /**
   * C++ original:
   * ```cpp
   * int     fVertexCount
   * ```
   */
  private var fVertexCount: Int = TODO("Initialize fVertexCount")

  /**
   * C++ original:
   * ```cpp
   * int     fIndexCount
   * ```
   */
  private var fIndexCount: Int = TODO("Initialize fIndexCount")

  /**
   * C++ original:
   * ```cpp
   * VertexMode fMode
   * ```
   */
  private var fMode: VertexMode = TODO("Initialize fMode")

  /**
   * C++ original:
   * ```cpp
   * uint32_t uniqueID() const { return fUniqueID; }
   * ```
   */
  public override fun uniqueID(): UInt {
    TODO("Implement uniqueID")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkRect& bounds() const { return fBounds; }
   * ```
   */
  public override fun bounds(): Int {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkVertices::approximateSize() const {
   *     return this->getSizes().fTotal;
   * }
   * ```
   */
  public override fun approximateSize(): ULong {
    TODO("Implement approximateSize")
  }

  /**
   * C++ original:
   * ```cpp
   * inline SkVerticesPriv SkVertices::priv() { return SkVerticesPriv(this); }
   * ```
   */
  public override fun priv(): SkVerticesPriv {
    TODO("Implement priv")
  }

  /**
   * C++ original:
   * ```cpp
   * inline const SkVerticesPriv SkVertices::priv() const {  // NOLINT(readability-const-return-type)
   *     return SkVerticesPriv(const_cast<SkVertices*>(this));
   * }
   * ```
   */
  public override fun toDelete(p: Unit?) {
    TODO("Implement toDelete")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkVertices::operator delete(void* p) {
   *     ::operator delete(p);
   * }
   * ```
   */
  public override fun getSizes(): Sizes {
    TODO("Implement getSizes")
  }

  public open class Builder public constructor(
    mode: undefined.VertexMode,
    vertexCount: Int,
    indexCount: Int,
    flags: UInt,
  ) {
    private var fVertices: Int = TODO("Initialize fVertices")

    private var fIntermediateFanIndices: Array<UByte>? = TODO("Initialize fIntermediateFanIndices")

    public constructor(desc: Desc) : this() {
      TODO("Implement constructor")
    }

    public fun isValid(): Boolean {
      TODO("Implement isValid")
    }

    public fun positions(): Int {
      TODO("Implement positions")
    }

    public fun indices(): UShort {
      TODO("Implement indices")
    }

    public fun texCoords(): Int {
      TODO("Implement texCoords")
    }

    public fun colors(): Int {
      TODO("Implement colors")
    }

    public fun detach(): Int {
      TODO("Implement detach")
    }

    private fun `init`(desc: Desc) {
      TODO("Implement init")
    }
  }

  public enum class VertexMode {
    kTriangles_VertexMode,
    kTriangleStrip_VertexMode,
    kTriangleFan_VertexMode,
    kLast_VertexMode,
  }

  public enum class BuilderFlags {
    kHasTexCoords_BuilderFlag,
    kHasColors_BuilderFlag,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkVertices> SkVertices::MakeCopy(VertexMode mode, int vertexCount,
     *                                        const SkPoint pos[], const SkPoint texs[],
     *                                        const SkColor colors[],
     *                                        int indexCount, const uint16_t indices[]) {
     *     auto desc = Desc{mode, vertexCount, indexCount, !!texs, !!colors};
     *     Builder builder(desc);
     *     if (!builder.isValid()) {
     *         return nullptr;
     *     }
     *
     *     Sizes sizes(desc);
     *     SkASSERT(sizes.isValid());
     *     sk_careful_memcpy(builder.positions(), pos, sizes.fVSize);
     *     sk_careful_memcpy(builder.texCoords(), texs, sizes.fTSize);
     *     sk_careful_memcpy(builder.colors(), colors, sizes.fCSize);
     *
     *     // The builder can update the number of indices.
     *     const size_t isize = ((mode == kTriangleFan_VertexMode) ? sizes.fBuilderTriFanISize
     *                                                             : sizes.fISize),
     *                 icount = isize / sizeof(uint16_t);
     *
     *     // Ensure that indices are valid for the given vertex count.
     *     SkASSERT(vertexCount > 0);
     *     const uint16_t max_index = SkToU16(vertexCount - 1);
     *
     *     size_t i = 0;
     *     for (; i + 8 <= icount; i += 8) {
     *         const skvx::ushort8 ind8 = skvx::ushort8::Load(indices + i),
     *                     clamped_ind8 = skvx::min(ind8, max_index);
     *         clamped_ind8.store(builder.indices() + i);
     *     }
     *     if (i + 4 <= icount) {
     *         const skvx::ushort4 ind4 = skvx::ushort4::Load(indices + i),
     *                     clamped_ind4 = skvx::min(ind4, max_index);
     *         clamped_ind4.store(builder.indices() + i);
     *
     *         i += 4;
     *     }
     *     if (i + 2 <= icount) {
     *         const skvx::ushort2 ind2 = skvx::ushort2::Load(indices + i),
     *                     clamped_ind2 = skvx::min(ind2, max_index);
     *         clamped_ind2.store(builder.indices() + i);
     *
     *         i += 2;
     *     }
     *     if (i < icount) {
     *         builder.indices()[i] = std::min(indices[i], max_index);
     *         SkDEBUGCODE(i += 1);
     *     }
     *     SkASSERT(i == icount);
     *
     *     return builder.detach();
     * }
     * ```
     */
    public override fun makeCopy(
      mode: VertexMode,
      vertexCount: Int,
      positions: Array<SkPoint>,
      texs: Array<SkPoint>,
      colors: Array<SkColor>,
      indexCount: Int,
      indices: Array<UShort>,
    ): Int {
      TODO("Implement makeCopy")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkVertices> MakeCopy(VertexMode mode, int vertexCount,
     *                                       const SkPoint positions[],
     *                                       const SkPoint texs[],
     *                                       const SkColor colors[]) {
     *         return MakeCopy(mode,
     *                         vertexCount,
     *                         positions,
     *                         texs,
     *                         colors,
     *                         0,
     *                         nullptr);
     *     }
     * ```
     */
    public override fun makeCopy(
      mode: VertexMode,
      vertexCount: Int,
      positions: Array<SkPoint>,
      texs: Array<SkPoint>,
      colors: Array<SkColor>,
    ): Int {
      TODO("Implement makeCopy")
    }
  }
}
