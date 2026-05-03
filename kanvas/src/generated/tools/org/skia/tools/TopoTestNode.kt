package org.skia.tools

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import org.skia.core.TArray
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import org.skia.math.SkRandom

/**
 * C++ original:
 * ```cpp
 * class TopoTestNode : public SkRefCnt {
 * public:
 *     TopoTestNode(int id) : fID(id) {}
 *
 *     void dependsOn(TopoTestNode* src) { *fDependencies.append() = src; }
 *     void targets(uint32_t target) { *fTargets.append() = target; }
 *
 *     int  id() const { return fID; }
 *     void reset() {
 *         fOutputPos = 0;
 *         fTempMark = false;
 *         fWasOutput = false;
 *     }
 *
 *     uint32_t outputPos() const {
 *         SkASSERT(fWasOutput);
 *         return fOutputPos;
 *     }
 *
 *     // check that the topological sort is valid for this node
 *     bool check() {
 *         if (!fWasOutput) {
 *             return false;
 *         }
 *
 *         for (int i = 0; i < fDependencies.size(); ++i) {
 *             if (!fDependencies[i]->fWasOutput) {
 *                 return false;
 *             }
 *             // This node should've been output after all the nodes on which it depends
 *             if (fOutputPos < fDependencies[i]->outputPos()) {
 *                 return false;
 *             }
 *         }
 *
 *         return true;
 *     }
 *
 *     // The following 7 methods are needed by the topological sort
 *     static void SetTempMark(TopoTestNode* node) { node->fTempMark = true; }
 *     static void ResetTempMark(TopoTestNode* node) { node->fTempMark = false; }
 *     static bool IsTempMarked(TopoTestNode* node) { return node->fTempMark; }
 *     static void Output(TopoTestNode* node, uint32_t outputPos) {
 *         SkASSERT(!node->fWasOutput);
 *         node->fOutputPos = outputPos;
 *         node->fWasOutput = true;
 *     }
 *     static bool          WasOutput(TopoTestNode* node) { return node->fWasOutput; }
 *     static uint32_t      GetIndex(TopoTestNode* node) { return node->outputPos(); }
 *     static int           NumDependencies(TopoTestNode* node) { return node->fDependencies.size(); }
 *     static TopoTestNode* Dependency(TopoTestNode* node, int index) {
 *         return node->fDependencies[index];
 *     }
 *     static int           NumTargets(TopoTestNode* node) { return node->fTargets.size(); }
 *     static uint32_t      GetTarget(TopoTestNode* node, int i) { return node->fTargets[i]; }
 *     static uint32_t      GetID(TopoTestNode* node) { return node->id(); }
 *
 *     // Helper functions for TopoSortBench & TopoSortTest
 *     static void AllocNodes(skia_private::TArray<sk_sp<ToolUtils::TopoTestNode>>* graph, int num) {
 *         graph->reserve_exact(graph->size() + num);
 *
 *         for (int i = 0; i < num; ++i) {
 *             graph->push_back(sk_sp<TopoTestNode>(new TopoTestNode(i)));
 *         }
 *     }
 *
 * #ifdef SK_DEBUG
 *     static void Print(const skia_private::TArray<TopoTestNode*>& graph) {
 *         for (int i = 0; i < graph.size(); ++i) {
 *             SkDebugf("%d, ", graph[i]->id());
 *         }
 *         SkDebugf("\n");
 *     }
 * #endif
 *
 *     // randomize the array
 *     static void Shuffle(SkSpan<sk_sp<TopoTestNode>> graph, SkRandom* rand) {
 *         for (size_t i = graph.size() - 1; i > 0; --i) {
 *             int swap = rand->nextU() % (i + 1);
 *
 *             graph[i].swap(graph[swap]);
 *         }
 *     }
 *
 *     SK_DECLARE_INTERNAL_LLIST_INTERFACE(TopoTestNode);
 *
 * private:
 *     int      fID;
 *     uint32_t fOutputPos = 0;
 *     bool     fTempMark = false;
 *     bool     fWasOutput = false;
 *
 *     SkTDArray<TopoTestNode*> fDependencies;
 *     SkTDArray<uint32_t>      fTargets;
 * }
 * ```
 */
public open class TopoTestNode public constructor(
  id: Int,
) : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * int      fID
   * ```
   */
  private var fID: Int = TODO("Initialize fID")

  /**
   * C++ original:
   * ```cpp
   * uint32_t fOutputPos
   * ```
   */
  private var fOutputPos: Int = TODO("Initialize fOutputPos")

  /**
   * C++ original:
   * ```cpp
   * bool     fTempMark = false
   * ```
   */
  private var fTempMark: Boolean = TODO("Initialize fTempMark")

  /**
   * C++ original:
   * ```cpp
   * bool     fWasOutput = false
   * ```
   */
  private var fWasOutput: Boolean = TODO("Initialize fWasOutput")

  /**
   * C++ original:
   * ```cpp
   * SkTDArray<TopoTestNode*> fDependencies
   * ```
   */
  private var fDependencies: Int = TODO("Initialize fDependencies")

  /**
   * C++ original:
   * ```cpp
   * SkTDArray<uint32_t>      fTargets
   * ```
   */
  private var fTargets: Int = TODO("Initialize fTargets")

  /**
   * C++ original:
   * ```cpp
   * void dependsOn(TopoTestNode* src) { *fDependencies.append() = src; }
   * ```
   */
  public fun dependsOn(src: TopoTestNode?) {
    TODO("Implement dependsOn")
  }

  /**
   * C++ original:
   * ```cpp
   * void targets(uint32_t target) { *fTargets.append() = target; }
   * ```
   */
  public fun targets(target: UInt) {
    TODO("Implement targets")
  }

  /**
   * C++ original:
   * ```cpp
   * int  id() const { return fID; }
   * ```
   */
  public fun id(): Int {
    TODO("Implement id")
  }

  /**
   * C++ original:
   * ```cpp
   * void reset() {
   *         fOutputPos = 0;
   *         fTempMark = false;
   *         fWasOutput = false;
   *     }
   * ```
   */
  public fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t outputPos() const {
   *         SkASSERT(fWasOutput);
   *         return fOutputPos;
   *     }
   * ```
   */
  public fun outputPos(): Int {
    TODO("Implement outputPos")
  }

  /**
   * C++ original:
   * ```cpp
   * bool check() {
   *         if (!fWasOutput) {
   *             return false;
   *         }
   *
   *         for (int i = 0; i < fDependencies.size(); ++i) {
   *             if (!fDependencies[i]->fWasOutput) {
   *                 return false;
   *             }
   *             // This node should've been output after all the nodes on which it depends
   *             if (fOutputPos < fDependencies[i]->outputPos()) {
   *                 return false;
   *             }
   *         }
   *
   *         return true;
   *     }
   * ```
   */
  public fun check(): Boolean {
    TODO("Implement check")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static void SetTempMark(TopoTestNode* node) { node->fTempMark = true; }
     * ```
     */
    public fun setTempMark(node: TopoTestNode?) {
      TODO("Implement setTempMark")
    }

    /**
     * C++ original:
     * ```cpp
     * static void ResetTempMark(TopoTestNode* node) { node->fTempMark = false; }
     * ```
     */
    public fun resetTempMark(node: TopoTestNode?) {
      TODO("Implement resetTempMark")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool IsTempMarked(TopoTestNode* node) { return node->fTempMark; }
     * ```
     */
    public fun isTempMarked(node: TopoTestNode?): Boolean {
      TODO("Implement isTempMarked")
    }

    /**
     * C++ original:
     * ```cpp
     * static void Output(TopoTestNode* node, uint32_t outputPos) {
     *         SkASSERT(!node->fWasOutput);
     *         node->fOutputPos = outputPos;
     *         node->fWasOutput = true;
     *     }
     * ```
     */
    public fun output(node: TopoTestNode?, outputPos: UInt) {
      TODO("Implement output")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool          WasOutput(TopoTestNode* node) { return node->fWasOutput; }
     * ```
     */
    public fun wasOutput(node: TopoTestNode?): Boolean {
      TODO("Implement wasOutput")
    }

    /**
     * C++ original:
     * ```cpp
     * static uint32_t      GetIndex(TopoTestNode* node) { return node->outputPos(); }
     * ```
     */
    public fun getIndex(node: TopoTestNode?): Int {
      TODO("Implement getIndex")
    }

    /**
     * C++ original:
     * ```cpp
     * static int           NumDependencies(TopoTestNode* node) { return node->fDependencies.size(); }
     * ```
     */
    public fun numDependencies(node: TopoTestNode?): Int {
      TODO("Implement numDependencies")
    }

    /**
     * C++ original:
     * ```cpp
     * static TopoTestNode* Dependency(TopoTestNode* node, int index) {
     *         return node->fDependencies[index];
     *     }
     * ```
     */
    public fun dependency(node: TopoTestNode?, index: Int): TopoTestNode {
      TODO("Implement dependency")
    }

    /**
     * C++ original:
     * ```cpp
     * static int           NumTargets(TopoTestNode* node) { return node->fTargets.size(); }
     * ```
     */
    public fun numTargets(node: TopoTestNode?): Int {
      TODO("Implement numTargets")
    }

    /**
     * C++ original:
     * ```cpp
     * static uint32_t      GetTarget(TopoTestNode* node, int i) { return node->fTargets[i]; }
     * ```
     */
    public fun getTarget(node: TopoTestNode?, i: Int): Int {
      TODO("Implement getTarget")
    }

    /**
     * C++ original:
     * ```cpp
     * static uint32_t      GetID(TopoTestNode* node) { return node->id(); }
     * ```
     */
    public fun getID(node: TopoTestNode?): Int {
      TODO("Implement getID")
    }

    /**
     * C++ original:
     * ```cpp
     * static void AllocNodes(skia_private::TArray<sk_sp<ToolUtils::TopoTestNode>>* graph, int num) {
     *         graph->reserve_exact(graph->size() + num);
     *
     *         for (int i = 0; i < num; ++i) {
     *             graph->push_back(sk_sp<TopoTestNode>(new TopoTestNode(i)));
     *         }
     *     }
     * ```
     */
    public fun allocNodes(graph: TArray<SkSp<TopoTestNode>>?, num: Int) {
      TODO("Implement allocNodes")
    }

    /**
     * C++ original:
     * ```cpp
     * static void Shuffle(SkSpan<sk_sp<TopoTestNode>> graph, SkRandom* rand) {
     *         for (size_t i = graph.size() - 1; i > 0; --i) {
     *             int swap = rand->nextU() % (i + 1);
     *
     *             graph[i].swap(graph[swap]);
     *         }
     *     }
     * ```
     */
    public fun shuffle(graph: SkSpan<SkSp<TopoTestNode>>, rand: SkRandom?) {
      TODO("Implement shuffle")
    }
  }
}
