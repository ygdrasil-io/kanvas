package org.skia.core

import kotlin.Int
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.utils.SkStrikeClient

/**
 * C++ original:
 * ```cpp
 * class SkStrikePromise {
 * public:
 *     SkStrikePromise() = delete;
 *     SkStrikePromise(const SkStrikePromise&) = delete;
 *     SkStrikePromise& operator=(const SkStrikePromise&) = delete;
 *     SkStrikePromise(SkStrikePromise&&);
 *     SkStrikePromise& operator=(SkStrikePromise&&);
 *
 *     explicit SkStrikePromise(sk_sp<SkStrike>&& strike);
 *     explicit SkStrikePromise(const SkStrikeSpec& spec);
 *
 *     // This only works when the GPU code is compiled in.
 *     static std::optional<SkStrikePromise> MakeFromBuffer(SkReadBuffer& buffer,
 *                                                          const SkStrikeClient* client,
 *                                                          SkStrikeCache* strikeCache);
 *     void flatten(SkWriteBuffer& buffer) const;
 *
 *     // Do what is needed to return a strike.
 *     SkStrike* strike();
 *
 *     // Reset the sk_sp<SkStrike> to nullptr.
 *     void resetStrike();
 *
 *     // Return a descriptor used to look up the SkStrike.
 *     const SkDescriptor& descriptor() const;
 *
 * private:
 *     std::variant<sk_sp<SkStrike>, std::unique_ptr<SkStrikeSpec>> fStrikeOrSpec;
 * }
 * ```
 */
public open class SkStrikePromise public constructor() {
  /**
   * C++ original:
   * ```cpp
   * SkStrikePromise() = delete
   * ```
   */
  public constructor(param0: SkStrikePromise) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkStrikePromise(const SkStrikePromise&) = delete
   * ```
   */
  public constructor(strike: SkSp<SkStrike>) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkStrikePromise(SkStrikePromise&&)
   * ```
   */
  public constructor(spec: SkStrikeSpec) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkStrikePromise& operator=(const SkStrikePromise&) = delete
   * ```
   */
  public fun assign(param0: SkStrikePromise) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * SkStrikePromise& operator=(SkStrikePromise&&)
   * ```
   */
  public fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkStrikePromise::flatten(SkWriteBuffer& buffer) const {
   *     this->descriptor().flatten(buffer);
   * }
   * ```
   */
  public fun strike(): SkStrike {
    TODO("Implement strike")
  }

  /**
   * C++ original:
   * ```cpp
   * SkStrike* SkStrikePromise::strike() {
   *     if (std::holds_alternative<std::unique_ptr<SkStrikeSpec>>(fStrikeOrSpec)) {
   *         // Turn the strike spec into a strike.
   *         std::unique_ptr<SkStrikeSpec> spec =
   *             std::exchange(std::get<std::unique_ptr<SkStrikeSpec>>(fStrikeOrSpec), nullptr);
   *
   *         fStrikeOrSpec = SkStrikeCache::GlobalStrikeCache()->findOrCreateStrike(*spec);
   *     }
   *     return std::get<sk_sp<SkStrike>>(fStrikeOrSpec).get();
   * }
   * ```
   */
  public fun resetStrike() {
    TODO("Implement resetStrike")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkStrikePromise::resetStrike() {
   *     fStrikeOrSpec = sk_sp<SkStrike>();
   * }
   * ```
   */
  public fun descriptor(): SkDescriptor {
    TODO("Implement descriptor")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * std::optional<SkStrikePromise> SkStrikePromise::MakeFromBuffer(
     *         SkReadBuffer& buffer, const SkStrikeClient* client, SkStrikeCache* strikeCache) {
     *     std::optional<SkAutoDescriptor> descriptor = SkAutoDescriptor::MakeFromBuffer(buffer);
     *     if (!buffer.validate(descriptor.has_value())) {
     *         return std::nullopt;
     *     }
     *
     *     // If there is a client, then this from a different process. Translate the SkTypefaceID from
     *     // the strike server (Renderer) process to strike client (GPU) process.
     *     if (client != nullptr) {
     *         if (!client->translateTypefaceID(&descriptor.value())) {
     *             return std::nullopt;
     *         }
     *     }
     *
     *     sk_sp<SkStrike> strike = strikeCache->findStrike(*descriptor->getDesc());
     *     SkASSERT(strike != nullptr);
     *     if (!buffer.validate(strike != nullptr)) {
     *         return std::nullopt;
     *     }
     *
     *     return SkStrikePromise{std::move(strike)};
     * }
     * ```
     */
    public fun makeFromBuffer(
      buffer: SkReadBuffer,
      client: SkStrikeClient?,
      strikeCache: SkStrikeCache?,
    ): Int {
      TODO("Implement makeFromBuffer")
    }
  }
}
