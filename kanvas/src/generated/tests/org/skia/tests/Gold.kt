package org.skia.tests

import kotlin.String
import kotlin.UInt

/**
 * C++ original:
 * ```cpp
 * struct Gold : public SkString {
 *     Gold() : SkString("") {}
 *     Gold(const SkString& sink, const SkString& src,
 *          const SkString& srcOptions, const SkString& name,
 *          const SkString& md5)
 *         : SkString("") {
 *         this->append(sink);
 *         this->append(src);
 *         this->append(srcOptions);
 *         this->append(name);
 *         this->append(md5);
 *     }
 *     struct Hash {
 *         uint32_t operator()(const Gold& g) const {
 *             return SkGoodHash()((const SkString&)g);
 *         }
 *     };
 * }
 * ```
 */
public open class Gold public constructor() : String(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * Gold() : SkString("") {}
   * ```
   */
  public constructor(
    sink: String,
    src: String,
    srcOptions: String,
    name: String,
    md5: String,
  ) : this(TODO()) {
    TODO("Implement constructor")
  }

  public open class Hash {
    public operator fun invoke(g: Gold): UInt {
      TODO("Implement invoke")
    }
  }
}
