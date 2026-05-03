package org.skia.core

import ChildPtr
import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import org.skia.gpu.ganesh.GrDirectContext
import org.skia.math.SkRect
import org.skia.tests.Result

/**
 * C++ original:
 * ```cpp
 * class SK_API SkMesh {
 * public:
 *     class IndexBuffer : public SkRefCnt {
 *     public:
 *         virtual size_t size() const = 0;
 *
 *         /**
 *          * Modifies the data in the IndexBuffer by copying size bytes from data into the buffer
 *          * at offset. Fails if offset + size > this->size() or if either offset or size is not
 *          * aligned to 4 bytes. The GrDirectContext* must match that used to create the buffer. We
 *          * take it as a parameter to emphasize that the context must be used to update the data and
 *          * thus the context must be valid for the current thread.
 *          */
 *         bool update(GrDirectContext*, const void* data, size_t offset, size_t size);
 *
 *     private:
 *         virtual bool onUpdate(GrDirectContext*, const void* data, size_t offset, size_t size) = 0;
 *     };
 *
 *     class VertexBuffer : public SkRefCnt {
 *     public:
 *         virtual size_t size() const = 0;
 *
 *         /**
 *          * Modifies the data in the IndexBuffer by copying size bytes from data into the buffer
 *          * at offset. Fails if offset + size > this->size() or if either offset or size is not
 *          * aligned to 4 bytes. The GrDirectContext* must match that used to create the buffer. We
 *          * take it as a parameter to emphasize that the context must be used to update the data and
 *          * thus the context must be valid for the current thread.
 *          */
 *         bool update(GrDirectContext*, const void* data, size_t offset, size_t size);
 *
 *     private:
 *         virtual bool onUpdate(GrDirectContext*, const void* data, size_t offset, size_t size) = 0;
 *     };
 *
 *     SkMesh();
 *     ~SkMesh();
 *
 *     SkMesh(const SkMesh&);
 *     SkMesh(SkMesh&&);
 *
 *     SkMesh& operator=(const SkMesh&);
 *     SkMesh& operator=(SkMesh&&);
 *
 *     enum class Mode { kTriangles, kTriangleStrip };
 *
 *     struct Result;
 *
 *     using ChildPtr = SkRuntimeEffect::ChildPtr;
 *
 *     /**
 *      * Creates a non-indexed SkMesh. The returned SkMesh can be tested for validity using
 *      * SkMesh::isValid(). An invalid mesh simply fails to draws if passed to SkCanvas::drawMesh().
 *      * If the mesh is invalid the returned string give contain the reason for the failure (e.g. the
 *      * vertex buffer was null or uniform data too small).
 *      */
 *     static Result Make(sk_sp<SkMeshSpecification>,
 *                        Mode,
 *                        sk_sp<VertexBuffer>,
 *                        size_t vertexCount,
 *                        size_t vertexOffset,
 *                        sk_sp<const SkData> uniforms,
 *                        SkSpan<ChildPtr> children,
 *                        const SkRect& bounds);
 *
 *     /**
 *      * Creates an indexed SkMesh. The returned SkMesh can be tested for validity using
 *      * SkMesh::isValid(). A invalid mesh simply fails to draw if passed to SkCanvas::drawMesh().
 *      * If the mesh is invalid the returned string give contain the reason for the failure (e.g. the
 *      * index buffer was null or uniform data too small).
 *      */
 *     static Result MakeIndexed(sk_sp<SkMeshSpecification>,
 *                               Mode,
 *                               sk_sp<VertexBuffer>,
 *                               size_t vertexCount,
 *                               size_t vertexOffset,
 *                               sk_sp<IndexBuffer>,
 *                               size_t indexCount,
 *                               size_t indexOffset,
 *                               sk_sp<const SkData> uniforms,
 *                               SkSpan<ChildPtr> children,
 *                               const SkRect& bounds);
 *
 *     sk_sp<SkMeshSpecification> refSpec() const { return fSpec; }
 *     SkMeshSpecification* spec() const { return fSpec.get(); }
 *
 *     Mode mode() const { return fMode; }
 *
 *     sk_sp<VertexBuffer> refVertexBuffer() const { return fVB; }
 *     VertexBuffer* vertexBuffer() const { return fVB.get(); }
 *
 *     size_t vertexOffset() const { return fVOffset; }
 *     size_t vertexCount()  const { return fVCount;  }
 *
 *     sk_sp<IndexBuffer> refIndexBuffer() const { return fIB; }
 *     IndexBuffer* indexBuffer() const { return fIB.get(); }
 *
 *     size_t indexOffset() const { return fIOffset; }
 *     size_t indexCount()  const { return fICount;  }
 *
 *     sk_sp<const SkData> refUniforms() const { return fUniforms; }
 *     const SkData* uniforms() const { return fUniforms.get(); }
 *
 *     SkSpan<const ChildPtr> children() const { return SkSpan(fChildren); }
 *
 *     SkRect bounds() const { return fBounds; }
 *
 *     bool isValid() const;
 *
 * private:
 *     std::tuple<bool, SkString> validate() const;
 *
 *     sk_sp<SkMeshSpecification> fSpec;
 *
 *     sk_sp<VertexBuffer> fVB;
 *     sk_sp<IndexBuffer>  fIB;
 *
 *     sk_sp<const SkData> fUniforms;
 *     skia_private::STArray<2, ChildPtr> fChildren;
 *
 *     size_t fVOffset = 0;  // Must be a multiple of spec->stride()
 *     size_t fVCount  = 0;
 *
 *     size_t fIOffset = 0;  // Must be a multiple of sizeof(uint16_t)
 *     size_t fICount  = 0;
 *
 *     Mode fMode = Mode::kTriangles;
 *
 *     SkRect fBounds = SkRect::MakeEmpty();
 * }
 * ```
 */
public data class SkMesh public constructor(
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkMeshSpecification> fSpec
   * ```
   */
  private var fSpec: Int,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<VertexBuffer> fVB
   * ```
   */
  private var fVB: Int,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<IndexBuffer>  fIB
   * ```
   */
  private var fIB: Int,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<const SkData> fUniforms
   * ```
   */
  private var fUniforms: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::STArray<2, ChildPtr> fChildren
   * ```
   */
  private var fChildren: Int,
  /**
   * C++ original:
   * ```cpp
   * size_t fVOffset = 0
   * ```
   */
  private var fVOffset: ULong,
  /**
   * C++ original:
   * ```cpp
   * size_t fVCount  = 0
   * ```
   */
  private var fVCount: ULong,
  /**
   * C++ original:
   * ```cpp
   * size_t fIOffset = 0
   * ```
   */
  private var fIOffset: ULong,
  /**
   * C++ original:
   * ```cpp
   * size_t fICount  = 0
   * ```
   */
  private var fICount: ULong,
  /**
   * C++ original:
   * ```cpp
   * Mode fMode = Mode::kTriangles
   * ```
   */
  private var fMode: Mode,
  /**
   * C++ original:
   * ```cpp
   * SkRect fBounds
   * ```
   */
  private var fBounds: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * SkMesh& SkMesh::operator=(const SkMesh&)
   * ```
   */
  private fun assign(param0: SkMesh) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMesh& SkMesh::operator=(SkMesh&&)
   * ```
   */
  private fun refSpec(): Int {
    TODO("Implement refSpec")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkMeshSpecification> refSpec() const { return fSpec; }
   * ```
   */
  private fun spec(): SkMeshSpecification {
    TODO("Implement spec")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMeshSpecification* spec() const { return fSpec.get(); }
   * ```
   */
  private fun mode(): Mode {
    TODO("Implement mode")
  }

  /**
   * C++ original:
   * ```cpp
   * Mode mode() const { return fMode; }
   * ```
   */
  private fun refVertexBuffer(): Int {
    TODO("Implement refVertexBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<VertexBuffer> refVertexBuffer() const { return fVB; }
   * ```
   */
  private fun vertexBuffer(): VertexBuffer {
    TODO("Implement vertexBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * VertexBuffer* vertexBuffer() const { return fVB.get(); }
   * ```
   */
  private fun vertexOffset(): ULong {
    TODO("Implement vertexOffset")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t vertexOffset() const { return fVOffset; }
   * ```
   */
  private fun vertexCount(): ULong {
    TODO("Implement vertexCount")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t vertexCount()  const { return fVCount;  }
   * ```
   */
  private fun refIndexBuffer(): Int {
    TODO("Implement refIndexBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<IndexBuffer> refIndexBuffer() const { return fIB; }
   * ```
   */
  private fun indexBuffer(): IndexBuffer {
    TODO("Implement indexBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * IndexBuffer* indexBuffer() const { return fIB.get(); }
   * ```
   */
  private fun indexOffset(): ULong {
    TODO("Implement indexOffset")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t indexOffset() const { return fIOffset; }
   * ```
   */
  private fun indexCount(): ULong {
    TODO("Implement indexCount")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t indexCount()  const { return fICount;  }
   * ```
   */
  private fun refUniforms(): Int {
    TODO("Implement refUniforms")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<const SkData> refUniforms() const { return fUniforms; }
   * ```
   */
  private fun uniforms(): Int {
    TODO("Implement uniforms")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkData* uniforms() const { return fUniforms.get(); }
   * ```
   */
  private fun children(): Int {
    TODO("Implement children")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const ChildPtr> children() const { return SkSpan(fChildren); }
   * ```
   */
  private fun bounds(): Int {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect bounds() const { return fBounds; }
   * ```
   */
  private fun isValid(): Boolean {
    TODO("Implement isValid")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkMesh::isValid() const {
   *     bool valid = SkToBool(fSpec);
   *     SkASSERT(valid == std::get<0>(this->validate()));
   *     return valid;
   * }
   * ```
   */
  private fun validate(): Int {
    TODO("Implement validate")
  }

  public abstract class IndexBuffer : SkRefCnt() {
    public abstract fun size(): ULong

    public fun update(
      dc: GrDirectContext?,
      `data`: Unit?,
      offset: ULong,
      size: ULong,
    ): Boolean {
      TODO("Implement update")
    }

    private abstract fun onUpdate(
      param0: GrDirectContext?,
      `data`: Unit?,
      offset: ULong,
      size: ULong,
    ): Boolean
  }

  public abstract class VertexBuffer : SkRefCnt() {
    public abstract fun size(): ULong

    public fun update(
      dc: GrDirectContext?,
      `data`: Unit?,
      offset: ULong,
      size: ULong,
    ): Boolean {
      TODO("Implement update")
    }

    private abstract fun onUpdate(
      param0: GrDirectContext?,
      `data`: Unit?,
      offset: ULong,
      size: ULong,
    ): Boolean
  }

  public enum class Mode {
    kTriangles,
    kTriangleStrip,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * SkMesh::Result SkMesh::Make(sk_sp<SkMeshSpecification> spec,
     *                             Mode mode,
     *                             sk_sp<VertexBuffer> vb,
     *                             size_t vertexCount,
     *                             size_t vertexOffset,
     *                             sk_sp<const SkData> uniforms,
     *                             SkSpan<ChildPtr> children,
     *                             const SkRect& bounds) {
     *     SkMesh mesh;
     *     mesh.fSpec     = std::move(spec);
     *     mesh.fMode     = mode;
     *     mesh.fVB       = std::move(vb);
     *     mesh.fUniforms = std::move(uniforms);
     *     mesh.fChildren.push_back_n(children.size(), children.data());
     *     mesh.fVCount   = vertexCount;
     *     mesh.fVOffset  = vertexOffset;
     *     mesh.fBounds   = bounds;
     *     auto [valid, msg] = mesh.validate();
     *     if (!valid) {
     *         mesh = {};
     *     }
     *     return {std::move(mesh), std::move(msg)};
     * }
     * ```
     */
    private fun make(
      spec: SkSp<SkMeshSpecification>,
      mode: Mode,
      vb: SkSp<org.skia.core.VertexBuffer>,
      vertexCount: ULong,
      vertexOffset: ULong,
      uniforms: SkSp<SkData>,
      children: SkSpan<ChildPtr>,
      bounds: SkRect,
    ): Result {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * SkMesh::Result SkMesh::MakeIndexed(sk_sp<SkMeshSpecification> spec,
     *                                    Mode mode,
     *                                    sk_sp<VertexBuffer> vb,
     *                                    size_t vertexCount,
     *                                    size_t vertexOffset,
     *                                    sk_sp<IndexBuffer> ib,
     *                                    size_t indexCount,
     *                                    size_t indexOffset,
     *                                    sk_sp<const SkData> uniforms,
     *                                    SkSpan<ChildPtr> children,
     *                                    const SkRect& bounds) {
     *     if (!ib) {
     *         // We check this before calling validate to disambiguate from a non-indexed mesh where
     *         // IB is expected to be null.
     *         return {{}, SkString{"An index buffer is required."}};
     *     }
     *     SkMesh mesh;
     *     mesh.fSpec     = std::move(spec);
     *     mesh.fMode     = mode;
     *     mesh.fVB       = std::move(vb);
     *     mesh.fVCount   = vertexCount;
     *     mesh.fVOffset  = vertexOffset;
     *     mesh.fIB       = std::move(ib);
     *     mesh.fUniforms = std::move(uniforms);
     *     mesh.fChildren.push_back_n(children.size(), children.data());
     *     mesh.fICount   = indexCount;
     *     mesh.fIOffset  = indexOffset;
     *     mesh.fBounds   = bounds;
     *     auto [valid, msg] = mesh.validate();
     *     if (!valid) {
     *         mesh = {};
     *     }
     *     return {std::move(mesh), std::move(msg)};
     * }
     * ```
     */
    private fun makeIndexed(
      spec: SkSp<SkMeshSpecification>,
      mode: Mode,
      vb: SkSp<org.skia.core.VertexBuffer>,
      vertexCount: ULong,
      vertexOffset: ULong,
      ib: SkSp<org.skia.core.IndexBuffer>,
      indexCount: ULong,
      indexOffset: ULong,
      uniforms: SkSp<SkData>,
      children: SkSpan<ChildPtr>,
      bounds: SkRect,
    ): Result {
      TODO("Implement makeIndexed")
    }
  }
}
