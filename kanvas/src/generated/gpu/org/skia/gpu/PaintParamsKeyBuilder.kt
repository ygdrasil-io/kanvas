package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import kotlin.ULong
import org.skia.foundation.SkSpan

/**
 * C++ original:
 * ```cpp
 * class PaintParamsKeyBuilder {
 * public:
 *     PaintParamsKeyBuilder(const PaintParamsKeyBuilder&) = delete;
 *     PaintParamsKeyBuilder& operator=(const PaintParamsKeyBuilder&) = delete;
 *
 *     PaintParamsKeyBuilder(PaintParamsKeyBuilder&&) = default;
 *     PaintParamsKeyBuilder& operator=(PaintParamsKeyBuilder&&) = default;
 *
 *     PaintParamsKeyBuilder(const ShaderCodeDictionary* dict) {
 *         SkDEBUGCODE(fDict = dict;)
 *     }
 *
 *     ~PaintParamsKeyBuilder() { SkASSERT(!fLocked); }
 *
 *     void beginBlock(BuiltInCodeSnippetID id) { this->beginBlock(static_cast<int32_t>(id)); }
 *     void beginBlock(int32_t codeSnippetID) {
 *         SkASSERT(!fLocked);
 *         SkDEBUGCODE(this->pushStack(codeSnippetID);)
 *         fData.push_back(codeSnippetID);
 *     }
 *
 *     // TODO: Have endBlock() be handled automatically with RAII, in which case we could have it
 *     // validate the snippet ID being popped off the stack frame.
 *     void endBlock() {
 *         SkDEBUGCODE(this->popStack();)
 *     }
 *
 * #ifdef SK_DEBUG
 *     // Check that the builder has been reset to its initial state prior to creating a new key.
 *     void checkReset();
 * #endif
 *
 *     // Helper to add blocks that don't have children
 *     void addBlock(BuiltInCodeSnippetID id) {
 *         this->beginBlock(id);
 *         this->endBlock();
 *     }
 *
 *     void addData(SkSpan<const uint32_t> data) {
 *         // First push the data size followed by the actual data.
 *         SkDEBUGCODE(this->validateData(data.size()));
 *         fData.push_back(data.size());
 *         fData.push_back_n(data.size(), data.data());
 *     }
 *
 *     void addErrorBlock() {
 *         fHasError = true;
 *         // Preserve the structure of parent stack, but since fHasError is true, the builder won't
 *         // produce a valid PaintParamsKey.
 *         this->addBlock(BuiltInCodeSnippetID::kError);
 *     }
 *
 *     void tryShrinkCapacity() {
 *         int halfCapacity = fData.capacity() / 2;
 *         if (fDataHighWaterMark < halfCapacity) {
 *             fDataHighWaterMark = 0;
 *             SkASSERT(fData.empty());
 *             fData.reserve_exact(halfCapacity);
 *         }
 *     }
 *
 * private:
 *     friend class AutoLockBuilderAsKey; // for lockAsKey() and unlock()
 *
 *     // Returns a view of this builder as a PaintParamsKey. The Builder cannot be used until the
 *     // returned Key goes out of scope.
 *     PaintParamsKey lockAsKey() {
 *         SkASSERT(!fLocked);       // lockAsKey() is not re-entrant
 *         SkASSERT(fStack.empty()); // All beginBlocks() had a matching endBlock()
 *
 *         SkDEBUGCODE(fLocked = true;)
 *         fDataHighWaterMark = std::max(fDataHighWaterMark, fData.size());
 *         return fHasError ? PaintParamsKey::Invalid() : PaintParamsKey(fData);
 *     }
 *
 *     // Invalidates any PaintParamsKey returned by lockAsKey() unless it has been cloned.
 *     void unlock() {
 *         SkASSERT(fLocked);
 *         fData.clear();
 *         fHasError = false;
 *
 *         SkDEBUGCODE(fLocked = false;)
 *         SkDEBUGCODE(fStack.clear();)
 *         SkDEBUGCODE(this->checkReset();)
 *     }
 *
 *     // The data array uses clear() on unlock so that it's underlying storage and repeated use of the
 *     // builder will hit a high-water mark and avoid lots of allocations when recording draws.
 *     skia_private::TArray<uint32_t> fData;
 *     bool fHasError = false; // if true, fData may not encode a valid/complete ShaderNode tree.
 *     int fDataHighWaterMark = 0;
 *
 * #ifdef SK_DEBUG
 *     void pushStack(int32_t codeSnippetID);
 *     void validateData(size_t dataSize);
 *     void popStack();
 *
 *     // Information about the current block being written
 *     struct StackFrame {
 *         int fCodeSnippetID;
 *         int fNumExpectedChildren;
 *         int fNumActualChildren = 0;
 *         int fDataSize = -1;
 *     };
 *
 *     const ShaderCodeDictionary* fDict;
 *     skia_private::TArray<StackFrame> fStack;
 *     bool fLocked = false;
 * #endif
 * }
 * ```
 */
public data class PaintParamsKeyBuilder public constructor(
  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<uint32_t> fData
   * ```
   */
  private var fData: Int,
  /**
   * C++ original:
   * ```cpp
   * bool fHasError = false
   * ```
   */
  private var fHasError: Boolean,
  /**
   * C++ original:
   * ```cpp
   * int fDataHighWaterMark = 0
   * ```
   */
  private var fDataHighWaterMark: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * PaintParamsKeyBuilder& operator=(const PaintParamsKeyBuilder&) = delete
   * ```
   */
  public fun assign(param0: PaintParamsKeyBuilder) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * PaintParamsKeyBuilder& operator=(PaintParamsKeyBuilder&&) = default
   * ```
   */
  public fun beginBlock(id: BuiltInCodeSnippetID) {
    TODO("Implement beginBlock")
  }

  /**
   * C++ original:
   * ```cpp
   * void beginBlock(BuiltInCodeSnippetID id) { this->beginBlock(static_cast<int32_t>(id)); }
   * ```
   */
  public fun beginBlock(codeSnippetID: Int) {
    TODO("Implement beginBlock")
  }

  /**
   * C++ original:
   * ```cpp
   * void beginBlock(int32_t codeSnippetID) {
   *         SkASSERT(!fLocked);
   *         SkDEBUGCODE(this->pushStack(codeSnippetID);)
   *         fData.push_back(codeSnippetID);
   *     }
   * ```
   */
  public fun endBlock() {
    TODO("Implement endBlock")
  }

  /**
   * C++ original:
   * ```cpp
   * void endBlock() {
   *         SkDEBUGCODE(this->popStack();)
   *     }
   * ```
   */
  public fun addBlock(id: BuiltInCodeSnippetID) {
    TODO("Implement addBlock")
  }

  /**
   * C++ original:
   * ```cpp
   * void addBlock(BuiltInCodeSnippetID id) {
   *         this->beginBlock(id);
   *         this->endBlock();
   *     }
   * ```
   */
  public fun addData(`data`: SkSpan<UInt>) {
    TODO("Implement addData")
  }

  /**
   * C++ original:
   * ```cpp
   * void addData(SkSpan<const uint32_t> data) {
   *         // First push the data size followed by the actual data.
   *         SkDEBUGCODE(this->validateData(data.size()));
   *         fData.push_back(data.size());
   *         fData.push_back_n(data.size(), data.data());
   *     }
   * ```
   */
  public fun addErrorBlock() {
    TODO("Implement addErrorBlock")
  }

  /**
   * C++ original:
   * ```cpp
   * void addErrorBlock() {
   *         fHasError = true;
   *         // Preserve the structure of parent stack, but since fHasError is true, the builder won't
   *         // produce a valid PaintParamsKey.
   *         this->addBlock(BuiltInCodeSnippetID::kError);
   *     }
   * ```
   */
  public fun tryShrinkCapacity() {
    TODO("Implement tryShrinkCapacity")
  }

  /**
   * C++ original:
   * ```cpp
   * void tryShrinkCapacity() {
   *         int halfCapacity = fData.capacity() / 2;
   *         if (fDataHighWaterMark < halfCapacity) {
   *             fDataHighWaterMark = 0;
   *             SkASSERT(fData.empty());
   *             fData.reserve_exact(halfCapacity);
   *         }
   *     }
   * ```
   */
  private fun lockAsKey(): PaintParamsKey {
    TODO("Implement lockAsKey")
  }

  /**
   * C++ original:
   * ```cpp
   * PaintParamsKey lockAsKey() {
   *         SkASSERT(!fLocked);       // lockAsKey() is not re-entrant
   *         SkASSERT(fStack.empty()); // All beginBlocks() had a matching endBlock()
   *
   *         SkDEBUGCODE(fLocked = true;)
   *         fDataHighWaterMark = std::max(fDataHighWaterMark, fData.size());
   *         return fHasError ? PaintParamsKey::Invalid() : PaintParamsKey(fData);
   *     }
   * ```
   */
  private fun unlock() {
    TODO("Implement unlock")
  }

  /**
   * C++ original:
   * ```cpp
   * void unlock() {
   *         SkASSERT(fLocked);
   *         fData.clear();
   *         fHasError = false;
   *
   *         SkDEBUGCODE(fLocked = false;)
   *         SkDEBUGCODE(fStack.clear();)
   *         SkDEBUGCODE(this->checkReset();)
   *     }
   * ```
   */
  public fun checkReset() {
    TODO("Implement checkReset")
  }

  /**
   * C++ original:
   * ```cpp
   * void PaintParamsKeyBuilder::checkReset() {
   *     SkASSERT(!fLocked);
   *     SkASSERT(fData.empty());
   *     SkASSERT(fStack.empty());
   *     SkASSERT(!fHasError);
   * }
   * ```
   */
  public fun pushStack(codeSnippetID: Int) {
    TODO("Implement pushStack")
  }

  /**
   * C++ original:
   * ```cpp
   * void PaintParamsKeyBuilder::pushStack(int32_t codeSnippetID) {
   *     SkASSERT(fDict->isValidID(codeSnippetID));
   *     // If the kError ID is pushed, fHasError must have been set already.
   *     SkASSERT(codeSnippetID != (int) BuiltInCodeSnippetID::kError || fHasError);
   *
   *     if (!fStack.empty()) {
   *         fStack.back().fNumActualChildren++;
   *         SkASSERT(fStack.back().fNumActualChildren <= fStack.back().fNumExpectedChildren);
   *     }
   *
   *     const ShaderSnippet* snippet = fDict->getEntry(codeSnippetID);
   *     fStack.push_back({codeSnippetID, snippet->fNumChildren});
   * }
   * ```
   */
  public fun validateData(dataSize: ULong) {
    TODO("Implement validateData")
  }

  /**
   * C++ original:
   * ```cpp
   * void PaintParamsKeyBuilder::validateData(size_t dataSize) {
   *     SkASSERT(!fStack.empty()); // addData() called within code snippet block
   *     // Check that addData() is only called for snippets that support it and is only called once
   *     const ShaderSnippet* snippet = fDict->getEntry(fStack.back().fCodeSnippetID);
   *     SkASSERT(snippet->storesSamplerDescData());
   *     SkASSERT(fStack.back().fDataSize < 0);
   *
   *     fStack.back().fDataSize = SkTo<int>(dataSize);
   * }
   * ```
   */
  public fun popStack() {
    TODO("Implement popStack")
  }
}
