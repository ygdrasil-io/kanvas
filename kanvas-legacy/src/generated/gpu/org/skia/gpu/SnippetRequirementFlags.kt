package org.skia.gpu

public enum class SnippetRequirementFlags {
  kNone,
  kLocalCoords,
  kPriorStageOutput,
  kBlenderDstColor,
  kPrimitiveColor,
  kGradientBuffer,
  kStoresSamplerDescData,
  kPassthroughLocalCoords,
  kLiftExpression,
  kOmitExpression,
}
