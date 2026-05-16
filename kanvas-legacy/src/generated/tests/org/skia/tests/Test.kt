package org.skia.tests

import graphite.TestOptions
import kotlin.Int
import kotlin.String
import org.skia.gpu.ContextOptions
import org.skia.gpu.ganesh.GrContextOptions

/**
 * C++ original:
 * ```cpp
 * struct Test {
 *     static Test MakeCPU(const char* name, CPUTestProc proc) {
 *         return Test{name, TestType::kCPU, CtsEnforcement::kNever,
 *                     proc, nullptr, nullptr, nullptr, nullptr};
 *     }
 *
 *     static Test MakeCPUSerial(const char* name, CPUTestProc proc) {
 *         return Test{name, TestType::kCPUSerial, CtsEnforcement::kNever,
 *                     proc, nullptr, nullptr, nullptr, nullptr};
 *     }
 *
 *     static Test MakeGanesh(const char* name,
 *                            CtsEnforcement ctsEnforcement,
 *                            GaneshTestProc proc,
 *                            GaneshContextOptionsProc optionsProc = nullptr) {
 *         return Test{name, TestType::kGanesh, ctsEnforcement,
 *                     nullptr, proc, nullptr, optionsProc, nullptr};
 *     }
 *
 *     static Test MakeGraphite(const char* name,
 *                              CtsEnforcement ctsEnforcement,
 *                              GraphiteTestProc proc,
 *                              GraphiteContextOptionsProc optionsProc = nullptr) {
 *         return Test{name, TestType::kGraphite, ctsEnforcement,
 *                     nullptr, nullptr, proc, nullptr, optionsProc};
 *     }
 *
 *     const char* fName;
 *     TestType fTestType;
 *     CtsEnforcement fCTSEnforcement;
 *     CPUTestProc fCPUProc = nullptr;
 *     GaneshTestProc fGaneshProc = nullptr;
 *     GraphiteTestProc fGraphiteProc = nullptr;
 *     GaneshContextOptionsProc fGaneshContextOptionsProc = nullptr;
 *     GraphiteContextOptionsProc fGraphiteContextOptionsProc = nullptr;
 *
 *     void modifyGrContextOptions(GrContextOptions* options) {
 *         if (fGaneshContextOptionsProc) {
 *             (*fGaneshContextOptionsProc)(options);
 *         }
 *     }
 *
 *     void modifyGraphiteContextOptions(skgpu::graphite::ContextOptions* options) {
 *         if (fGraphiteContextOptionsProc) {
 *             (*fGraphiteContextOptionsProc)(options);
 *         }
 *     }
 *
 *     void cpu(skiatest::Reporter* r) const {
 *         SkASSERT(this->fTestType == TestType::kCPU ||
 *                  this->fTestType == TestType::kCPUSerial);
 *         TRACE_EVENT1("test_cpu", TRACE_FUNC, "name", this->fName/*these are static*/);
 *         this->fCPUProc(r);
 *     }
 *
 *     void ganesh(skiatest::Reporter* r, const GrContextOptions& options) const {
 *         SkASSERT(this->fTestType == TestType::kGanesh);
 *         TRACE_EVENT1("test_ganesh", TRACE_FUNC, "name", this->fName/*these are static*/);
 *         this->fGaneshProc(r, options);
 *     }
 *
 *     void graphite(skiatest::Reporter* r, const graphite::TestOptions& options) const {
 *         SkASSERT(this->fTestType == TestType::kGraphite);
 *         TRACE_EVENT1("test_graphite", TRACE_FUNC, "name", this->fName/*these are static*/);
 *         this->fGraphiteProc(r, options);
 *     }
 *
 * private:
 *     Test(const char* name,
 *          TestType testType,
 *          CtsEnforcement ctsEnforcement,
 *          CPUTestProc cpuProc,
 *          GaneshTestProc ganeshProc,
 *          GraphiteTestProc graphiteProc,
 *          GaneshContextOptionsProc ganeshOptionsProc,
 *          GraphiteContextOptionsProc graphiteOptionsProc)
 *             : fName(name)
 *             , fTestType(testType)
 *             , fCTSEnforcement(ctsEnforcement)
 *             , fCPUProc(cpuProc)
 *             , fGaneshProc(ganeshProc)
 *             , fGraphiteProc(graphiteProc)
 *             , fGaneshContextOptionsProc(ganeshOptionsProc)
 *             , fGraphiteContextOptionsProc(graphiteOptionsProc) {}
 * }
 * ```
 */
public data class Test public constructor(
  /**
   * C++ original:
   * ```cpp
   * const char* fName
   * ```
   */
  public val fName: String?,
  /**
   * C++ original:
   * ```cpp
   * TestType fTestType
   * ```
   */
  public var fTestType: Int,
  /**
   * C++ original:
   * ```cpp
   * CtsEnforcement fCTSEnforcement
   * ```
   */
  public var fCTSEnforcement: Int,
  /**
   * C++ original:
   * ```cpp
   * CPUTestProc fCPUProc = nullptr
   * ```
   */
  public var fCPUProc: CPUTestProc,
  /**
   * C++ original:
   * ```cpp
   * GaneshTestProc fGaneshProc = nullptr
   * ```
   */
  public var fGaneshProc: GaneshTestProc,
  /**
   * C++ original:
   * ```cpp
   * GraphiteTestProc fGraphiteProc = nullptr
   * ```
   */
  public var fGraphiteProc: GraphiteTestProc,
  /**
   * C++ original:
   * ```cpp
   * GaneshContextOptionsProc fGaneshContextOptionsProc = nullptr
   * ```
   */
  public var fGaneshContextOptionsProc: GaneshContextOptionsProc,
  /**
   * C++ original:
   * ```cpp
   * GraphiteContextOptionsProc fGraphiteContextOptionsProc = nullptr
   * ```
   */
  public var fGraphiteContextOptionsProc: GraphiteContextOptionsProc,
) {
  /**
   * C++ original:
   * ```cpp
   * void modifyGrContextOptions(GrContextOptions* options) {
   *         if (fGaneshContextOptionsProc) {
   *             (*fGaneshContextOptionsProc)(options);
   *         }
   *     }
   * ```
   */
  public fun modifyGrContextOptions(options: GrContextOptions?) {
    TODO("Implement modifyGrContextOptions")
  }

  /**
   * C++ original:
   * ```cpp
   * void modifyGraphiteContextOptions(skgpu::graphite::ContextOptions* options) {
   *         if (fGraphiteContextOptionsProc) {
   *             (*fGraphiteContextOptionsProc)(options);
   *         }
   *     }
   * ```
   */
  public fun modifyGraphiteContextOptions(options: ContextOptions?) {
    TODO("Implement modifyGraphiteContextOptions")
  }

  /**
   * C++ original:
   * ```cpp
   * void cpu(skiatest::Reporter* r) const {
   *         SkASSERT(this->fTestType == TestType::kCPU ||
   *                  this->fTestType == TestType::kCPUSerial);
   *         TRACE_EVENT1("test_cpu", TRACE_FUNC, "name", this->fName/*these are static*/);
   *         this->fCPUProc(r);
   *     }
   * ```
   */
  public fun cpu(r: Reporter?) {
    TODO("Implement cpu")
  }

  /**
   * C++ original:
   * ```cpp
   * void ganesh(skiatest::Reporter* r, const GrContextOptions& options) const {
   *         SkASSERT(this->fTestType == TestType::kGanesh);
   *         TRACE_EVENT1("test_ganesh", TRACE_FUNC, "name", this->fName/*these are static*/);
   *         this->fGaneshProc(r, options);
   *     }
   * ```
   */
  public fun ganesh(r: Reporter?, options: GrContextOptions) {
    TODO("Implement ganesh")
  }

  /**
   * C++ original:
   * ```cpp
   * void graphite(skiatest::Reporter* r, const graphite::TestOptions& options) const {
   *         SkASSERT(this->fTestType == TestType::kGraphite);
   *         TRACE_EVENT1("test_graphite", TRACE_FUNC, "name", this->fName/*these are static*/);
   *         this->fGraphiteProc(r, options);
   *     }
   * ```
   */
  public fun graphite(r: Reporter?, options: TestOptions) {
    TODO("Implement graphite")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static Test MakeCPU(const char* name, CPUTestProc proc) {
     *         return Test{name, TestType::kCPU, CtsEnforcement::kNever,
     *                     proc, nullptr, nullptr, nullptr, nullptr};
     *     }
     * ```
     */
    public fun makeCPU(name: String?, proc: CPUTestProc): Test {
      TODO("Implement makeCPU")
    }

    /**
     * C++ original:
     * ```cpp
     * static Test MakeCPUSerial(const char* name, CPUTestProc proc) {
     *         return Test{name, TestType::kCPUSerial, CtsEnforcement::kNever,
     *                     proc, nullptr, nullptr, nullptr, nullptr};
     *     }
     * ```
     */
    public fun makeCPUSerial(name: String?, proc: CPUTestProc): Test {
      TODO("Implement makeCPUSerial")
    }

    /**
     * C++ original:
     * ```cpp
     * static Test MakeGanesh(const char* name,
     *                            CtsEnforcement ctsEnforcement,
     *                            GaneshTestProc proc,
     *                            GaneshContextOptionsProc optionsProc = nullptr) {
     *         return Test{name, TestType::kGanesh, ctsEnforcement,
     *                     nullptr, proc, nullptr, optionsProc, nullptr};
     *     }
     * ```
     */
    public fun makeGanesh(
      name: String?,
      ctsEnforcement: CtsEnforcement,
      proc: GaneshTestProc,
      optionsProc: GaneshContextOptionsProc = TODO(),
    ): Test {
      TODO("Implement makeGanesh")
    }

    /**
     * C++ original:
     * ```cpp
     * static Test MakeGraphite(const char* name,
     *                              CtsEnforcement ctsEnforcement,
     *                              GraphiteTestProc proc,
     *                              GraphiteContextOptionsProc optionsProc = nullptr) {
     *         return Test{name, TestType::kGraphite, ctsEnforcement,
     *                     nullptr, nullptr, proc, nullptr, optionsProc};
     *     }
     * ```
     */
    public fun makeGraphite(
      name: String?,
      ctsEnforcement: CtsEnforcement,
      proc: GraphiteTestProc,
      optionsProc: GraphiteContextOptionsProc = TODO(),
    ): Test {
      TODO("Implement makeGraphite")
    }
  }
}
