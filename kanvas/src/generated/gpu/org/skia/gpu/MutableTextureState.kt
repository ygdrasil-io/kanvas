package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import org.skia.foundation.SkRefCnt
import undefined.StateData

/**
 * C++ original:
 * ```cpp
 * class SK_API MutableTextureState : public SkRefCnt {
 * public:
 *     MutableTextureState();
 *     ~MutableTextureState() override;
 *
 *     MutableTextureState(const MutableTextureState& that);
 *
 *     MutableTextureState& operator=(const MutableTextureState& that);
 *
 *     void set(const MutableTextureState& that);
 *
 *     BackendApi backend() const { return fBackend; }
 *
 *     // Returns true if the backend mutable state has been initialized.
 *     bool isValid() const { return fIsValid; }
 *
 * private:
 *     friend class MutableTextureStateData;
 *     friend class MutableTextureStatePriv;
 *     // Size determined by looking at the MutableTextureStateData subclasses, then
 *     // guessing-and-checking. Compiler will complain if this is too small - in that case,
 *     // just increase the number.
 *     inline constexpr static size_t kMaxSubclassSize = 16;
 *     using AnyStateData = SkAnySubclass<MutableTextureStateData, kMaxSubclassSize>;
 *
 *     template <typename StateData>
 *     MutableTextureState(BackendApi api, const StateData& data) : fBackend(api), fIsValid(true) {
 *         fStateData.emplace<StateData>(data);
 *     }
 *
 *     AnyStateData fStateData;
 *
 *     BackendApi fBackend;
 *     bool fIsValid;
 * }
 * ```
 */
public open class MutableTextureState public constructor() : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * inline constexpr static size_t kMaxSubclassSize = 16
   * ```
   */
  private var fStateData: Int = TODO("Initialize fStateData")

  /**
   * C++ original:
   * ```cpp
   * AnyStateData fStateData
   * ```
   */
  private var fBackend: BackendApi = TODO("Initialize fBackend")

  /**
   * C++ original:
   * ```cpp
   * BackendApi fBackend
   * ```
   */
  private var fIsValid: Boolean = TODO("Initialize fIsValid")

  /**
   * C++ original:
   * ```cpp
   * MutableTextureState::MutableTextureState():
   *     fBackend(BackendApi::kUnsupported),
   *     fIsValid(false) {}
   * ```
   */
  public constructor(that: MutableTextureState) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * MutableTextureState::MutableTextureState(const MutableTextureState& that): fIsValid(false) {
   *     this->set(that);
   * }
   * ```
   */
  public constructor(api: BackendApi, `data`: StateData) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * MutableTextureState& MutableTextureState::operator=(const MutableTextureState& that) {
   *     if (this != &that) {
   *         this->set(that);
   *     }
   *     return *this;
   * }
   * ```
   */
  public fun assign(that: MutableTextureState) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * void MutableTextureState::set(const MutableTextureState& that) {
   *     SkASSERT(!fIsValid || this->fBackend == that.fBackend);
   *     fIsValid = that.fIsValid;
   *     fBackend = that.fBackend;
   *     if (!fIsValid) {
   *         return;
   *     }
   *     fStateData.reset();
   *     switch (fBackend) {
   *         case BackendApi::kVulkan:
   *             that.fStateData->copyTo(fStateData);
   *             break;
   *         default:
   *             SK_ABORT("Unknown BackendApi");
   *     }
   * }
   * ```
   */
  public fun `set`(that: MutableTextureState) {
    TODO("Implement set")
  }

  /**
   * C++ original:
   * ```cpp
   * BackendApi backend() const { return fBackend; }
   * ```
   */
  public fun backend(): BackendApi {
    TODO("Implement backend")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isValid() const { return fIsValid; }
   * ```
   */
  public fun isValid(): Boolean {
    TODO("Implement isValid")
  }

  public companion object {
    private val kMaxSubclassSize: ULong = TODO("Initialize kMaxSubclassSize")
  }
}
