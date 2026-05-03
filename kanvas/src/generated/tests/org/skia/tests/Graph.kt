package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import org.skia.foundation.SkSp
import org.skia.memory.SkTDArray

/**
 * C++ original:
 * ```cpp
 * class Graph {
 * public:
 *     Graph(int numNodesToReserve, skiatest::Reporter* reporter)
 *             : fNodes(numNodesToReserve)
 *             , fReporter(reporter) {
 *     }
 *
 *     Node* addNode(uint32_t id) {
 *         this->validate();
 *         sk_sp<Node> tmp(new Node(id));
 *
 *         fNodes.push_back(tmp);       // The graph gets the creation ref
 *         tmp->setIndexInSort(fNodes.size()-1);
 *         this->validate();
 *         return tmp.get();
 *     }
 *
 *     // 'dependedOn' must appear before 'dependent' in the sort
 *     void addEdge(Node* dependedOn, Node* dependent) {
 *         // TODO: this would be faster if all the SkTDArray code was stripped out of
 *         // addEdges but, when used in MDB sorting, this entry point will never be used.
 *         SkTDArray<Node*> tmp(&dependedOn, 1);
 *         this->addEdges(&tmp, dependent);
 *     }
 *
 *     // All the nodes in 'dependedOn' must appear before 'dependent' in the sort.
 *     // This is O(v + e + cost_of_sorting(b)) where:
 *     //    v: number of nodes
 *     //    e: number of edges
 *     //    b: number of new edges in 'dependedOn'
 *     //
 *     // The algorithm works by first finding the "affected region" that contains all the
 *     // nodes whose position in the topological sort is invalidated by the addition of the new
 *     // edges. It then traverses the affected region from left to right, temporarily removing
 *     // invalid nodes from 'fNodes' and shifting valid nodes left to fill in the gaps. In this
 *     // left to right traversal, when a node is shifted to the left the current set of invalid
 *     // nodes is examined to see if any needed to be moved to the right of that node. If so,
 *     // they are reintroduced to the 'fNodes' array but now in the appropriate position. The
 *     // separation of the algorithm into search (the dfs method) and readjustment (the shift
 *     // method) means that each node affected by the new edges is only ever moved once.
 *     void addEdges(SkTDArray<Node*>* dependedOn, Node* dependent) {
 *         this->validate();
 *
 *         // remove any of the new dependencies that are already satisfied
 *         for (int i = 0; i < dependedOn->size(); ++i) {
 *             if ((*dependedOn)[i]->indexInSort() < dependent->indexInSort()) {
 *                 dependent->addDependency((*dependedOn)[i]);
 *                 dependedOn->removeShuffle(i);
 *                 i--;
 *             } else {
 *                 dependent->addDependency((*dependedOn)[i]);
 *             }
 *         }
 *
 *         if (dependedOn->empty()) {
 *             return;
 *         }
 *
 *         // Sort the remaining dependencies into descending order based on their indices in the
 *         // sort. This means that we will be proceeding from right to left in the sort when
 *         // correcting the order.
 *         // TODO: QSort is waaay overkill here!
 *         SkTQSort<Node*>(dependedOn->begin(), dependedOn->end(), Node::CompareIndicesGT);
 *
 *         // TODO: although this is the general algorithm, I think this can be simplified for our
 *         // use case (i.e., the same dependent for all the new edges).
 *
 *         int lowerBound = fNodes.size();  // 'lowerBound' tracks the left of the affected region
 *         for (int i = 0; i < dependedOn->size(); ++i) {
 *             if ((*dependedOn)[i]->indexInSort() < lowerBound) {
 *                 this->shift(lowerBound);
 *             }
 *
 *             if (!dependent->visited()) {
 *                 this->dfs(dependent, (*dependedOn)[i]->indexInSort());
 *             }
 *
 *             lowerBound = std::min(dependent->indexInSort(), lowerBound);
 *         }
 *
 *         this->shift(lowerBound);
 *
 *         this->validate();
 *     }
 *
 *     // Get the list of node ids in the current sorted order
 *     void getActual(SkString* actual) const {
 *         this->validate();
 *
 *         for (int i = 0; i < fNodes.size(); ++i) {
 *             (*actual) += fNodes[i]->id();
 *             if (i < fNodes.size()-1) {
 *                 (*actual) += ',';
 *             }
 *         }
 *     }
 *
 * #ifdef SK_DEBUG
 *     void print() const {
 *         SkDebugf("-------------------\n");
 *         for (int i = 0; i < fNodes.size(); ++i) {
 *             if (fNodes[i]) {
 *                 SkDebugf("%c ", fNodes[i]->id());
 *             } else {
 *                 SkDebugf("0 ");
 *             }
 *         }
 *         SkDebugf("\n");
 *
 *         for (int i = 0; i < fNodes.size(); ++i) {
 *             if (fNodes[i]) {
 *                 fNodes[i]->print();
 *             }
 *         }
 *
 *         SkDebugf("Stack: ");
 *         for (int i = 0; i < fStack.size(); ++i) {
 *            SkDebugf("%c/%c ", fStack[i].fNode->id(), fStack[i].fDest->id());
 *         }
 *         SkDebugf("\n");
 *     }
 * #endif
 *
 * private:
 *     void validate() const {
 *         REPORTER_ASSERT(fReporter, fStack.empty());
 *
 *         for (int i = 0; i < fNodes.size(); ++i) {
 *             REPORTER_ASSERT(fReporter, fNodes[i]->indexInSort() == i);
 *
 *             fNodes[i]->validate(fReporter);
 *         }
 *
 *         // All the nodes in the Queue had better have been marked as visited
 *         for (int i = 0; i < fStack.size(); ++i) {
 *             SkASSERT(fStack[i].fNode->visited());
 *         }
 *     }
 *
 *     // Collect the nodes that need to be moved within the affected region. All the nodes found
 *     // to be in violation of the topological constraints are placed in 'fStack'.
 *     void dfs(Node* node, int upperBound) {
 *         node->setVisited(true);
 *
 *         for (int i = 0; i < node->numDependents(); ++i) {
 *             Node* dependent = node->dependent(i);
 *
 *             SkASSERT(dependent->indexInSort() != upperBound); // this would be a cycle
 *
 *             if (!dependent->visited() && dependent->indexInSort() < upperBound) {
 *                 this->dfs(dependent, upperBound);
 *             }
 *         }
 *
 *         fStack.push_back({ sk_ref_sp(node), fNodes[upperBound].get() });
 *     }
 *
 *     // Move 'node' to the index-th slot of the sort. The index-th slot should not have a current
 *     // occupant.
 *     void moveNodeInSort(const sk_sp<Node>& node, int index) {
 *         SkASSERT(!fNodes[index]);
 *         fNodes[index] = node;
 *         node->setIndexInSort(index);
 *     }
 *
 * #ifdef SK_DEBUG
 *     // Does 'fStack' have 'node'? That is, was 'node' discovered to be in violation of the
 *     // topological constraints?
 *     bool stackContains(Node* node) {
 *         for (int i = 0; i < fStack.size(); ++i) {
 *             if (node == fStack[i].fNode.get()) {
 *                 return true;
 *             }
 *         }
 *         return false;
 *     }
 * #endif
 *
 *     // The 'shift' method iterates through the affected area from left to right moving Nodes that
 *     // were found to be in violation of the topological order (i.e., in 'fStack') to their correct
 *     // locations and shifting the non-violating nodes left, into the holes the violating nodes left
 *     // behind.
 *     void shift(int index) {
 *         int numRemoved = 0;
 *         while (!fStack.empty()) {
 *             sk_sp<Node> node = fNodes[index];
 *
 *             if (node->visited()) {
 *                 // This node is in 'fStack' and was found to be in violation of the topological
 *                 // constraints. Remove it from 'fNodes' so non-violating nodes can be shifted
 *                 // left.
 *                 SkASSERT(this->stackContains(node.get()));
 *                 node->setVisited(false);  // reset for future use
 *                 fNodes[index] = nullptr;
 *                 numRemoved++;
 *             } else {
 *                 // This node was found to not be in violation of any topological constraints but
 *                 // must be moved left to fill in for those that were.
 *                 SkASSERT(!this->stackContains(node.get()));
 *                 SkASSERT(numRemoved); // should be moving left
 *
 *                 this->moveNodeInSort(node, index - numRemoved);
 *                 fNodes[index] = nullptr;
 *             }
 *
 *             while (!fStack.empty() && node.get() == fStack.back().fDest) {
 *                 // The left to right loop has finally encountered the destination for one or more
 *                 // of the nodes in 'fStack'. Place them to the right of 'node' in the sort. Note
 *                 // that because the violating nodes were already removed from 'fNodes' there
 *                 // should be enough empty space for them to be placed now.
 *                 numRemoved--;
 *
 *                 this->moveNodeInSort(fStack.back().fNode, index - numRemoved);
 *
 *                 fStack.pop_back();
 *             }
 *
 *             index++;
 *         }
 *     }
 *
 *     TArray<sk_sp<Node>> fNodes;
 *
 *     struct StackInfo {
 *         sk_sp<Node> fNode;  // This gets a ref bc, in 'shift' it will be pulled out of 'fNodes'
 *         Node*       fDest;
 *     };
 *
 *     TArray<StackInfo>   fStack;     // only used in addEdges()
 *
 *     skiatest::Reporter*   fReporter;
 * }
 * ```
 */
public data class Graph public constructor(
  /**
   * C++ original:
   * ```cpp
   * TArray<sk_sp<Node>> fNodes
   * ```
   */
  private var fNodes: Int,
  /**
   * C++ original:
   * ```cpp
   * TArray<StackInfo>   fStack
   * ```
   */
  private var fStack: Int,
  /**
   * C++ original:
   * ```cpp
   * skiatest::Reporter*   fReporter
   * ```
   */
  private var fReporter: Reporter?,
) {
  /**
   * C++ original:
   * ```cpp
   * Node* addNode(uint32_t id) {
   *         this->validate();
   *         sk_sp<Node> tmp(new Node(id));
   *
   *         fNodes.push_back(tmp);       // The graph gets the creation ref
   *         tmp->setIndexInSort(fNodes.size()-1);
   *         this->validate();
   *         return tmp.get();
   *     }
   * ```
   */
  public fun addNode(id: UInt): Node {
    TODO("Implement addNode")
  }

  /**
   * C++ original:
   * ```cpp
   * void addEdge(Node* dependedOn, Node* dependent) {
   *         // TODO: this would be faster if all the SkTDArray code was stripped out of
   *         // addEdges but, when used in MDB sorting, this entry point will never be used.
   *         SkTDArray<Node*> tmp(&dependedOn, 1);
   *         this->addEdges(&tmp, dependent);
   *     }
   * ```
   */
  public fun addEdge(dependedOn: Node?, dependent: Node?) {
    TODO("Implement addEdge")
  }

  /**
   * C++ original:
   * ```cpp
   * void addEdges(SkTDArray<Node*>* dependedOn, Node* dependent) {
   *         this->validate();
   *
   *         // remove any of the new dependencies that are already satisfied
   *         for (int i = 0; i < dependedOn->size(); ++i) {
   *             if ((*dependedOn)[i]->indexInSort() < dependent->indexInSort()) {
   *                 dependent->addDependency((*dependedOn)[i]);
   *                 dependedOn->removeShuffle(i);
   *                 i--;
   *             } else {
   *                 dependent->addDependency((*dependedOn)[i]);
   *             }
   *         }
   *
   *         if (dependedOn->empty()) {
   *             return;
   *         }
   *
   *         // Sort the remaining dependencies into descending order based on their indices in the
   *         // sort. This means that we will be proceeding from right to left in the sort when
   *         // correcting the order.
   *         // TODO: QSort is waaay overkill here!
   *         SkTQSort<Node*>(dependedOn->begin(), dependedOn->end(), Node::CompareIndicesGT);
   *
   *         // TODO: although this is the general algorithm, I think this can be simplified for our
   *         // use case (i.e., the same dependent for all the new edges).
   *
   *         int lowerBound = fNodes.size();  // 'lowerBound' tracks the left of the affected region
   *         for (int i = 0; i < dependedOn->size(); ++i) {
   *             if ((*dependedOn)[i]->indexInSort() < lowerBound) {
   *                 this->shift(lowerBound);
   *             }
   *
   *             if (!dependent->visited()) {
   *                 this->dfs(dependent, (*dependedOn)[i]->indexInSort());
   *             }
   *
   *             lowerBound = std::min(dependent->indexInSort(), lowerBound);
   *         }
   *
   *         this->shift(lowerBound);
   *
   *         this->validate();
   *     }
   * ```
   */
  public fun addEdges(dependedOn: SkTDArray<Node?>?, dependent: Node?) {
    TODO("Implement addEdges")
  }

  /**
   * C++ original:
   * ```cpp
   * void getActual(SkString* actual) const {
   *         this->validate();
   *
   *         for (int i = 0; i < fNodes.size(); ++i) {
   *             (*actual) += fNodes[i]->id();
   *             if (i < fNodes.size()-1) {
   *                 (*actual) += ',';
   *             }
   *         }
   *     }
   * ```
   */
  public fun getActual(`actual`: String?) {
    TODO("Implement getActual")
  }

  /**
   * C++ original:
   * ```cpp
   * void print() const {
   *         SkDebugf("-------------------\n");
   *         for (int i = 0; i < fNodes.size(); ++i) {
   *             if (fNodes[i]) {
   *                 SkDebugf("%c ", fNodes[i]->id());
   *             } else {
   *                 SkDebugf("0 ");
   *             }
   *         }
   *         SkDebugf("\n");
   *
   *         for (int i = 0; i < fNodes.size(); ++i) {
   *             if (fNodes[i]) {
   *                 fNodes[i]->print();
   *             }
   *         }
   *
   *         SkDebugf("Stack: ");
   *         for (int i = 0; i < fStack.size(); ++i) {
   *            SkDebugf("%c/%c ", fStack[i].fNode->id(), fStack[i].fDest->id());
   *         }
   *         SkDebugf("\n");
   *     }
   * ```
   */
  public fun print() {
    TODO("Implement print")
  }

  /**
   * C++ original:
   * ```cpp
   * void validate() const {
   *         REPORTER_ASSERT(fReporter, fStack.empty());
   *
   *         for (int i = 0; i < fNodes.size(); ++i) {
   *             REPORTER_ASSERT(fReporter, fNodes[i]->indexInSort() == i);
   *
   *             fNodes[i]->validate(fReporter);
   *         }
   *
   *         // All the nodes in the Queue had better have been marked as visited
   *         for (int i = 0; i < fStack.size(); ++i) {
   *             SkASSERT(fStack[i].fNode->visited());
   *         }
   *     }
   * ```
   */
  private fun validate() {
    TODO("Implement validate")
  }

  /**
   * C++ original:
   * ```cpp
   * void dfs(Node* node, int upperBound) {
   *         node->setVisited(true);
   *
   *         for (int i = 0; i < node->numDependents(); ++i) {
   *             Node* dependent = node->dependent(i);
   *
   *             SkASSERT(dependent->indexInSort() != upperBound); // this would be a cycle
   *
   *             if (!dependent->visited() && dependent->indexInSort() < upperBound) {
   *                 this->dfs(dependent, upperBound);
   *             }
   *         }
   *
   *         fStack.push_back({ sk_ref_sp(node), fNodes[upperBound].get() });
   *     }
   * ```
   */
  private fun dfs(node: Node?, upperBound: Int) {
    TODO("Implement dfs")
  }

  /**
   * C++ original:
   * ```cpp
   * void moveNodeInSort(const sk_sp<Node>& node, int index) {
   *         SkASSERT(!fNodes[index]);
   *         fNodes[index] = node;
   *         node->setIndexInSort(index);
   *     }
   * ```
   */
  private fun moveNodeInSort(node: SkSp<Node>, index: Int) {
    TODO("Implement moveNodeInSort")
  }

  /**
   * C++ original:
   * ```cpp
   * bool stackContains(Node* node) {
   *         for (int i = 0; i < fStack.size(); ++i) {
   *             if (node == fStack[i].fNode.get()) {
   *                 return true;
   *             }
   *         }
   *         return false;
   *     }
   * ```
   */
  private fun stackContains(node: Node?): Boolean {
    TODO("Implement stackContains")
  }

  /**
   * C++ original:
   * ```cpp
   * void shift(int index) {
   *         int numRemoved = 0;
   *         while (!fStack.empty()) {
   *             sk_sp<Node> node = fNodes[index];
   *
   *             if (node->visited()) {
   *                 // This node is in 'fStack' and was found to be in violation of the topological
   *                 // constraints. Remove it from 'fNodes' so non-violating nodes can be shifted
   *                 // left.
   *                 SkASSERT(this->stackContains(node.get()));
   *                 node->setVisited(false);  // reset for future use
   *                 fNodes[index] = nullptr;
   *                 numRemoved++;
   *             } else {
   *                 // This node was found to not be in violation of any topological constraints but
   *                 // must be moved left to fill in for those that were.
   *                 SkASSERT(!this->stackContains(node.get()));
   *                 SkASSERT(numRemoved); // should be moving left
   *
   *                 this->moveNodeInSort(node, index - numRemoved);
   *                 fNodes[index] = nullptr;
   *             }
   *
   *             while (!fStack.empty() && node.get() == fStack.back().fDest) {
   *                 // The left to right loop has finally encountered the destination for one or more
   *                 // of the nodes in 'fStack'. Place them to the right of 'node' in the sort. Note
   *                 // that because the violating nodes were already removed from 'fNodes' there
   *                 // should be enough empty space for them to be placed now.
   *                 numRemoved--;
   *
   *                 this->moveNodeInSort(fStack.back().fNode, index - numRemoved);
   *
   *                 fStack.pop_back();
   *             }
   *
   *             index++;
   *         }
   *     }
   * ```
   */
  private fun shift(index: Int) {
    TODO("Implement shift")
  }

  public data class StackInfo public constructor(
    public var fNode: SkSp<Node>,
    public var fDest: Node?,
  )
}
