package org.skia.gpu

public enum class DstUsage {
  kNone,
  kDependsOnDst,
  kDstReadRequired,
  kAdvancedBlend,
}
