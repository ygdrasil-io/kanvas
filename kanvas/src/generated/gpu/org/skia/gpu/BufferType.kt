package org.skia.gpu

public enum class BufferType {
  kVertex,
  kIndex,
  kXferCpuToGpu,
  kXferGpuToCpu,
  kUniform,
  kStorage,
  kQuery,
  kIndirect,
  kVertexStorage,
  kIndexStorage,
  kLast,
}
