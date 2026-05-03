package org.skia.gpu

public enum class PathRendererStrategy {
  kTessellation,
  kTessellationAndSmallAtlas,
  kRasterAtlas,
  kComputeAnalyticAA,
  kComputeMSAA16,
  kComputeMSAA8,
}
