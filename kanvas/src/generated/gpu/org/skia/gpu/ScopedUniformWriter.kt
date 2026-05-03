package org.skia.gpu

/**
 * C++ original:
 * ```cpp
 * class ScopedUniformWriter {
 * public:
 *     ScopedUniformWriter(const KeyContext& keyContext, BuiltInCodeSnippetID codeSnippetID)
 *             : ScopedUniformWriter(keyContext.pipelineDataGatherer(),
 *                                   keyContext.dict()->getEntry(codeSnippetID)) {}
 *
 *     ~ScopedUniformWriter() {
 *         if (fGatherer) {
 *             fGatherer->endStruct();
 *         }
 *     }
 *
 * private:
 *     ScopedUniformWriter(PipelineDataGatherer* gatherer, const ShaderSnippet* snippet)
 * #if defined(SK_DEBUG)
 *         : fValidator(gatherer, snippet->fUniforms, SkToBool(snippet->fUniformStructName))
 * #endif
 *     {
 *         if (snippet->fUniformStructName) {
 *             gatherer->beginStruct(snippet->fRequiredAlignment);
 *             fGatherer = gatherer;
 *         } else {
 *             fGatherer = nullptr;
 *         }
 *     }
 *
 *     PipelineDataGatherer* fGatherer;
 *     SkDEBUGCODE(UniformExpectationsValidator fValidator;)
 * }
 * ```
 */
public data class ScopedUniformWriter public constructor(
  /**
   * C++ original:
   * ```cpp
   * PipelineDataGatherer* fGatherer
   * ```
   */
  private var fGatherer: PipelineDataGatherer?,
)
