package org.skia.gpu

import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import kotlin.UInt

/**
 * C++ original:
 * ```cpp
 * class VerticesRenderStep final : public RenderStep {
 * public:
 *     explicit VerticesRenderStep(Layout, PrimitiveType, bool hasColor, bool hasTexCoords);
 *
 *     ~VerticesRenderStep() override;
 *
 *     std::string vertexSkSL() const override;
 *     void writeVertices(DrawWriter* writer,
 *                        const DrawParams& params,
 *                        uint32_t ssboIndex) const override;
 *     void writeUniformsAndTextures(const DrawParams&, PipelineDataGatherer*) const override;
 *     const char* fragmentColorSkSL() const override;
 *
 * private:
 *     const bool fHasColor;
 *     const bool fHasTexCoords;
 * }
 * ```
 */
public class VerticesRenderStep public constructor(
  layout: Layout,
  type: PrimitiveType,
  hasColor: Boolean,
  hasTexCoords: Boolean,
) : RenderStep(TODO(), TODO(), TODO(), TODO(), TODO(), TODO(), TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * const bool fHasColor
   * ```
   */
  private val fHasColor: Boolean = TODO("Initialize fHasColor")

  /**
   * C++ original:
   * ```cpp
   * const bool fHasTexCoords
   * ```
   */
  private val fHasTexCoords: Boolean = TODO("Initialize fHasTexCoords")

  /**
   * C++ original:
   * ```cpp
   * std::string VerticesRenderStep::vertexSkSL() const {
   *     if (fHasColor && fHasTexCoords) {
   *         return
   *             "color = half4(vertColor.bgr * vertColor.a, vertColor.a);\n"
   *             "float4 devPosition = localToDevice * float4(position, 0.0, 1.0);\n"
   *             "devPosition.z = depth;\n"
   *             "stepLocalCoords = texCoords;\n";
   *     } else if (fHasTexCoords) {
   *         return
   *             "float4 devPosition = localToDevice * float4(position, 0.0, 1.0);\n"
   *             "devPosition.z = depth;\n"
   *             "stepLocalCoords = texCoords;\n";
   *     } else if (fHasColor) {
   *         return
   *             "color = half4(vertColor.bgr * vertColor.a, vertColor.a);\n"
   *             "float4 devPosition = localToDevice * float4(position, 0.0, 1.0);\n"
   *             "devPosition.z = depth;\n"
   *             "stepLocalCoords = position;\n";
   *     } else {
   *         return
   *             "float4 devPosition = localToDevice * float4(position, 0.0, 1.0);\n"
   *             "devPosition.z = depth;\n"
   *             "stepLocalCoords = position;\n";
   *     }
   * }
   * ```
   */
  public override fun vertexSkSL(): Int {
    TODO("Implement vertexSkSL")
  }

  /**
   * C++ original:
   * ```cpp
   * void VerticesRenderStep::writeVertices(DrawWriter* writer,
   *                                        const DrawParams& params,
   *                                        uint32_t ssboIndex) const {
   *     SkVerticesPriv info(params.geometry().vertices()->priv());
   *     const int vertexCount = info.vertexCount();
   *     const int indexCount = info.indexCount();
   *     const SkPoint* positions = info.positions();
   *     const uint16_t* indices = info.indices();
   *     const SkColor* colors = info.colors();
   *     const SkPoint* texCoords = info.texCoords();
   *
   *     // This should always be the case if the Renderer was chosen appropriately, but the vertex
   *     // writing loop is set up in such a way that if the shader expects color or tex coords and they
   *     // are missing, it will just read 0s, so release builds are safe.
   *     SkASSERT(fHasColor == SkToBool(colors));
   *     SkASSERT(fHasTexCoords == SkToBool(texCoords));
   *
   *     // TODO: We could access the writer's DrawBufferManager and upload the SkVertices index buffer
   *     // but that would require we manually manage the VertexWriter for interleaving the position,
   *     // color, and tex coord arrays together. This wouldn't be so bad if we let ::Vertices() take
   *     // a CPU index buffer that indexes into the accumulated vertex data (and handles offsetting for
   *     // merged drawIndexed calls), or if we could bind multiple attribute sources and copy the
   *     // position/color/texCoord data separately in bulk w/o using an Appender.
   *     DrawWriter::Vertices verts{*writer};
   *     verts.reserve(indices ? indexCount : vertexCount);
   *
   *     VertState state(vertexCount, indices, indexCount);
   *     VertState::Proc vertProc = state.chooseProc(info.mode());
   *     while (vertProc(&state)) {
   *         verts.append(3) << positions[state.f0]
   *                         << VertexWriter::If(fHasColor, colors ? colors[state.f0]
   *                                                               : SK_ColorTRANSPARENT)
   *                         << VertexWriter::If(fHasTexCoords, texCoords ? texCoords[state.f0]
   *                                                                      : SkPoint{0.f, 0.f})
   *                         << ssboIndex
   *                         << positions[state.f1]
   *                         << VertexWriter::If(fHasColor, colors ? colors[state.f1]
   *                                                               : SK_ColorTRANSPARENT)
   *                         << VertexWriter::If(fHasTexCoords, texCoords ? texCoords[state.f1]
   *                                                                      : SkPoint{0.f, 0.f})
   *                         << ssboIndex
   *                         << positions[state.f2]
   *                         << VertexWriter::If(fHasColor, colors ? colors[state.f2]
   *                                                               : SK_ColorTRANSPARENT)
   *                         << VertexWriter::If(fHasTexCoords, texCoords ? texCoords[state.f2]
   *                                                                      : SkPoint{0.f, 0.f})
   *                         << ssboIndex;
   *     }
   * }
   * ```
   */
  public override fun writeVertices(
    writer: DrawWriter?,
    params: DrawParams,
    ssboIndex: UInt,
  ) {
    TODO("Implement writeVertices")
  }

  /**
   * C++ original:
   * ```cpp
   * void VerticesRenderStep::writeUniformsAndTextures(const DrawParams& params,
   *                                                   PipelineDataGatherer* gatherer) const {
   *     // Vertices are transformed on the GPU. Store PaintDepth as a uniform to avoid copying the
   *     // same depth for each vertex.
   *     SkDEBUGCODE(gatherer->checkRewind());
   *     SkDEBUGCODE(UniformExpectationsValidator uev(gatherer, this->uniforms());)
   *     gatherer->write(params.transform().matrix());
   *     gatherer->write(params.order().depthAsFloat());
   * }
   * ```
   */
  public override fun writeUniformsAndTextures(param0: DrawParams, gatherer: PipelineDataGatherer?) {
    TODO("Implement writeUniformsAndTextures")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* VerticesRenderStep::fragmentColorSkSL() const {
   *     if (fHasColor) {
   *         return "primitiveColor = color;\n";
   *     } else {
   *         return "";
   *     }
   * }
   * ```
   */
  public override fun fragmentColorSkSL(): Char {
    TODO("Implement fragmentColorSkSL")
  }
}
