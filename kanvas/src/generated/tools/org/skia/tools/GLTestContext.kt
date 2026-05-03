package org.skia.tools

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkSp
import org.skia.gpu.ganesh.GrContextOptions
import org.skia.gpu.ganesh.GrEGLImage
import org.skia.gpu.ganesh.GrGLInterface
import undefined.Ret

/**
 * C++ original:
 * ```cpp
 * class GLTestContext : public TestContext {
 * public:
 *     ~GLTestContext() override;
 *
 *     GrBackendApi backend() override { return GrBackendApi::kOpenGL; }
 *
 *     /** Does this represent a successfully created GL context? */
 *     bool isValid() const;
 *
 *     const GrGLInterface* gl() const { return fGLInterface.get(); }
 *
 *     /** Used for testing EGLImage integration. Take a GL_TEXTURE_2D and wraps it in an EGL Image */
 *     virtual GrEGLImage texture2DToEGLImage(GrGLuint /*texID*/) const { return nullptr; }
 *
 *     virtual void destroyEGLImage(GrEGLImage) const { }
 *
 *     /**
 *      * Used for testing EGLImage integration. Takes a EGLImage and wraps it in a
 *      * GL_TEXTURE_EXTERNAL_OES.
 *      */
 *     virtual GrGLuint eglImageToExternalTexture(GrEGLImage) const { return 0; }
 *
 *     void testAbandon() override;
 *
 *     void overrideVersion(const char* version, const char* shadingLanguageVersion);
 *
 *     /**
 *      * Creates a new GL context of the same type and makes the returned context current
 *      * (if not null).
 *      */
 *     virtual std::unique_ptr<GLTestContext> makeNew() const { return nullptr; }
 *
 *     template<typename Ret, typename... Args>
 *     void getGLProcAddress(Ret(GR_GL_FUNCTION_TYPE** out)(Args...),
 *                           const char* name, const char* ext = nullptr) const {
 *         using Proc = Ret(GR_GL_FUNCTION_TYPE*)(Args...);
 *         if (!SkStrStartsWith(name, "gl")) {
 *             SK_ABORT("getGLProcAddress: proc name must have 'gl' prefix");
 *             *out = nullptr;
 *         } else if (ext) {
 *             SkString fullname(name);
 *             fullname.append(ext);
 *             *out = reinterpret_cast<Proc>(this->onPlatformGetProcAddress(fullname.c_str()));
 *         } else {
 *             *out = reinterpret_cast<Proc>(this->onPlatformGetProcAddress(name));
 *         }
 *     }
 *
 *     sk_sp<GrDirectContext> makeContext(const GrContextOptions& options) override;
 *
 * protected:
 *     GLTestContext();
 *
 *     /*
 *      * Methods that subclasses must call from their constructors and destructors.
 *      */
 *     void init(sk_sp<const GrGLInterface>);
 *
 *     void teardown() override;
 *
 *     virtual GrGLFuncPtr onPlatformGetProcAddress(const char *) const = 0;
 *
 * private:
 *     /** Subclass provides the gl interface object if construction was successful. */
 *     sk_sp<const GrGLInterface> fOriginalGLInterface;
 *
 *     /** The same as fOriginalGLInterface unless the version has been overridden. */
 *     sk_sp<const GrGLInterface> fGLInterface;
 *
 *     using INHERITED = TestContext;
 * }
 * ```
 */
public abstract class GLTestContext public constructor() : TestContext() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<const GrGLInterface> fOriginalGLInterface
   * ```
   */
  private var fOriginalGLInterface: Int = TODO("Initialize fOriginalGLInterface")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<const GrGLInterface> fGLInterface
   * ```
   */
  private var fGLInterface: Int = TODO("Initialize fGLInterface")

  /**
   * C++ original:
   * ```cpp
   * GrBackendApi backend() override { return GrBackendApi::kOpenGL; }
   * ```
   */
  public override fun backend(): Int {
    TODO("Implement backend")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isValid() const
   * ```
   */
  public fun isValid(): Boolean {
    TODO("Implement isValid")
  }

  /**
   * C++ original:
   * ```cpp
   * const GrGLInterface* gl() const { return fGLInterface.get(); }
   * ```
   */
  public fun gl(): Int {
    TODO("Implement gl")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual GrEGLImage texture2DToEGLImage(GrGLuint /*texID*/) const { return nullptr; }
   * ```
   */
  public open fun texture2DToEGLImage(param0: Int): Int {
    TODO("Implement texture2DToEGLImage")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void destroyEGLImage(GrEGLImage) const { }
   * ```
   */
  public open fun destroyEGLImage(param0: GrEGLImage) {
    TODO("Implement destroyEGLImage")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual GrGLuint eglImageToExternalTexture(GrEGLImage) const { return 0; }
   * ```
   */
  public open fun eglImageToExternalTexture(param0: GrEGLImage): Int {
    TODO("Implement eglImageToExternalTexture")
  }

  /**
   * C++ original:
   * ```cpp
   * void testAbandon() override
   * ```
   */
  public override fun testAbandon() {
    TODO("Implement testAbandon")
  }

  /**
   * C++ original:
   * ```cpp
   * void overrideVersion(const char* version, const char* shadingLanguageVersion)
   * ```
   */
  public fun overrideVersion(version: String?, shadingLanguageVersion: String?) {
    TODO("Implement overrideVersion")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual std::unique_ptr<GLTestContext> makeNew() const { return nullptr; }
   * ```
   */
  public open fun makeNew(): Int {
    TODO("Implement makeNew")
  }

  /**
   * C++ original:
   * ```cpp
   *     template<typename Ret, typename... Args>
   *     void getGLProcAddress(Ret(GR_GL_FUNCTION_TYPE** out)(Args...),
   *                           const char* name, const char* ext = nullptr) const {
   *         using Proc = Ret(GR_GL_FUNCTION_TYPE*)(Args...);
   *         if (!SkStrStartsWith(name, "gl")) {
   *             SK_ABORT("getGLProcAddress: proc name must have 'gl' prefix");
   *             *out = nullptr;
   *         } else if (ext) {
   *             SkString fullname(name);
   *             fullname.append(ext);
   *             *out = reinterpret_cast<Proc>(this->onPlatformGetProcAddress(fullname.c_str()));
   *         } else {
   *             *out = reinterpret_cast<Proc>(this->onPlatformGetProcAddress(name));
   *         }
   *     }
   * ```
   */
  public fun <Ret, Args> getGLProcAddress(
    param0: (Any) -> Ret,
    name: String?,
    ext: String? = TODO(),
  ) {
    TODO("Implement getGLProcAddress")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<GrDirectContext> makeContext(const GrContextOptions& options) override
   * ```
   */
  public override fun makeContext(options: GrContextOptions): Int {
    TODO("Implement makeContext")
  }

  /**
   * C++ original:
   * ```cpp
   * void init(sk_sp<const GrGLInterface>)
   * ```
   */
  protected fun `init`(param0: SkSp<GrGLInterface>) {
    TODO("Implement init")
  }

  /**
   * C++ original:
   * ```cpp
   * void teardown() override
   * ```
   */
  protected override fun teardown() {
    TODO("Implement teardown")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual GrGLFuncPtr onPlatformGetProcAddress(const char *) const = 0
   * ```
   */
  protected abstract fun onPlatformGetProcAddress(param0: String?): Int
}
