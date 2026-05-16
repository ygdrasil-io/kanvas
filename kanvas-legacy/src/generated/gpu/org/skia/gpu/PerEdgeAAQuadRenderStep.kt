package org.skia.gpu

import kotlin.Char
import kotlin.Int
import kotlin.UInt

/**
 * C++ original:
 * ```cpp
 * class PerEdgeAAQuadRenderStep final : public RenderStep {
 * public:
 *     PerEdgeAAQuadRenderStep(Layout, StaticBufferManager*);
 *
 *     ~PerEdgeAAQuadRenderStep() override;
 *
 *     std::string vertexSkSL() const override;
 *     const char* fragmentCoverageSkSL() const override;
 *
 *     void writeVertices(DrawWriter*, const DrawParams&, uint32_t ssboIndex) const override;
 *     void writeUniformsAndTextures(const DrawParams&, PipelineDataGatherer*) const override;
 *
 * private:
 *     // Points to the static buffers holding the fixed indexed vertex template for drawing instances.
 *     BindBufferInfo fVertexBuffer;
 *     BindBufferInfo fIndexBuffer;
 * }
 * ```
 */
public class PerEdgeAAQuadRenderStep public constructor(
  param0: Layout,
  param1: StaticBufferManager,
) : RenderStep() {
  /**
   * C++ original:
   * ```cpp
   * BindBufferInfo fVertexBuffer
   * ```
   */
  private var fVertexBuffer: Int = TODO("Initialize fVertexBuffer")

  /**
   * C++ original:
   * ```cpp
   * BindBufferInfo fIndexBuffer
   * ```
   */
  private var fIndexBuffer: Int = TODO("Initialize fIndexBuffer")

  /**
   * C++ original:
   * ```cpp
   * PerEdgeAAQuadRenderStep(Layout, StaticBufferManager*)
   * ```
   */
  public constructor(layout: Layout, bufferManager: StaticBufferManager?) : this(TODO(), TODO(), TODO(), TODO(), TODO(), TODO(), TODO(), TODO(), TODO()) {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * std::string PerEdgeAAQuadRenderStep::vertexSkSL() const {
   *     // Returns the body of a vertex function, which must define a float4 devPosition variable and
   *     // must write to an already-defined float2 stepLocalCoords variable.
   *     return "float4 devPosition = per_edge_aa_quad_vertex_fn("
   *                    // Static Data Attributes
   *                    "cornerID, normal, "
   *                    // Append Data Attributes
   *                    "edgeFlags, quadXs, quadYs, depth, "
   *                    "float3x3(mat0, mat1, mat2), "
   *                    // Varyings
   *                    "edgeDistances, "
   *                    // Render Step
   *                    "stepLocalCoords);\n";
   * }
   * ```
   */
  public override fun vertexSkSL(): Int {
    TODO("Implement vertexSkSL")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* PerEdgeAAQuadRenderStep::fragmentCoverageSkSL() const {
   *     // The returned SkSL must write its coverage into a 'half4 outputCoverage' variable (defined in
   *     // the calling code) with the actual coverage splatted out into all four channels.
   *     return "outputCoverage = per_edge_aa_quad_coverage_fn(sk_FragCoord, edgeDistances);";
   * }
   * ```
   */
  public override fun fragmentCoverageSkSL(): Char {
    TODO("Implement fragmentCoverageSkSL")
  }

  /**
   * C++ original:
   * ```cpp
   * void PerEdgeAAQuadRenderStep::writeVertices(DrawWriter* writer,
   *                                            const DrawParams& params,
   *                                            uint32_t ssboIndex) const {
   *     SkASSERT(params.geometry().isEdgeAAQuad());
   *     const EdgeAAQuad& quad = params.geometry().edgeAAQuad();
   *
   *     DrawWriter::Instances instance{*writer, fVertexBuffer, fIndexBuffer, kIndexCount};
   *     auto vw = instance.append(1);
   *
   *     // Empty fills should not have been recorded at all.
   *     SkDEBUGCODE(Rect bounds = params.geometry().bounds());
   *     SkASSERT(!bounds.isEmptyNegativeOrNaN());
   *
   *     constexpr uint8_t kAAOn = 255;
   *     constexpr uint8_t kAAOff = 0;
   *     auto edgeSigns = skvx::byte4{quad.edgeFlags() & AAFlags::kLeft   ? kAAOn : kAAOff,
   *                                  quad.edgeFlags() & AAFlags::kTop    ? kAAOn : kAAOff,
   *                                  quad.edgeFlags() & AAFlags::kRight  ? kAAOn : kAAOff,
   *                                  quad.edgeFlags() & AAFlags::kBottom ? kAAOn : kAAOff};
   *
   *     // The vertex shader expects points to be in clockwise order. EdgeAAQuad is the only
   *     // shape that *might* have counter-clockwise input.
   *     if (is_clockwise(quad)) {
   *         vw << edgeSigns << quad.xs() << quad.ys();
   *     } else {
   *         vw << skvx::shuffle<2,1,0,3>(edgeSigns)  // swap left and right AA bits
   *            << skvx::shuffle<1,0,3,2>(quad.xs())  // swap TL with TR, and BL with BR
   *            << skvx::shuffle<1,0,3,2>(quad.ys()); //   ""
   *     }
   *
   *     // All instance types share the remaining instance attribute definitions
   *     const SkM44& m = params.transform().matrix();
   *
   *     vw << params.order().depthAsFloat()
   *        << ssboIndex
   *        << m.rc(0,0) << m.rc(1,0) << m.rc(3,0)  // mat0
   *        << m.rc(0,1) << m.rc(1,1) << m.rc(3,1)  // mat1
   *        << m.rc(0,3) << m.rc(1,3) << m.rc(3,3); // mat2
   * }
   * ```
   */
  public override fun writeVertices(
    writer: DrawWriter?,
    param1: DrawParams,
    ssboIndex: UInt,
  ) {
    TODO("Implement writeVertices")
  }

  /**
   * C++ original:
   * ```cpp
   * void PerEdgeAAQuadRenderStep::writeUniformsAndTextures(const DrawParams&,
   *                                                        PipelineDataGatherer* gatherer) const {
   *     // All data is uploaded as instance attributes, so no uniforms are needed.
   *     SkDEBUGCODE(gatherer->checkRewind());
   * }
   * ```
   */
  public override fun writeUniformsAndTextures(param0: DrawParams, gatherer: PipelineDataGatherer?) {
    TODO("Implement writeUniformsAndTextures")
  }
}
