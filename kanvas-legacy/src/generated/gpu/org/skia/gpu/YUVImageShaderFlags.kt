package org.skia.gpu

public enum class YUVImageShaderFlags {
  kNone,
  kHardwareSamplingNoSwizzle,
  kHardwareSampling,
  kShaderBasedSampling,
  kCubicSampling,
  kExcludeCubic,
  kNoCubicNoNonSwizzledHW,
}
