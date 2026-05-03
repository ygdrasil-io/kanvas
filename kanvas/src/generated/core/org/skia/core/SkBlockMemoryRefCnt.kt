package org.skia.core

import org.skia.foundation.SkRefCnt

/**
 * C++ original:
 * ```cpp
 * class SkBlockMemoryRefCnt : public SkRefCnt {
 * public:
 *     explicit SkBlockMemoryRefCnt(SkDynamicMemoryWStream::Block* head) : fHead(head) { }
 *
 *     ~SkBlockMemoryRefCnt() override {
 *         SkDynamicMemoryWStream::Block* block = fHead;
 *         while (block != nullptr) {
 *             SkDynamicMemoryWStream::Block* next = block->fNext;
 *             sk_free(block);
 *             block = next;
 *         }
 *     }
 *
 *     SkDynamicMemoryWStream::Block* const fHead;
 * }
 * ```
 */
public open class SkBlockMemoryRefCnt public constructor(
  head: Block?,
) : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * SkDynamicMemoryWStream::Block* const fHead
   * ```
   */
  public val fHead: Block? = TODO("Initialize fHead")
}
