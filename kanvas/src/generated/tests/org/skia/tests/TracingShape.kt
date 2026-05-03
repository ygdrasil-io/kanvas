package org.skia.tests

import kotlin.Char
import kotlin.String

/**
 * C++ original:
 * ```cpp
 * struct TracingShape {
 *     TracingShape() {
 *         TRACE_EVENT_OBJECT_CREATED_WITH_ID("skia.objects", this->typeName(), this);
 *     }
 *     virtual ~TracingShape() {
 *         TRACE_EVENT_OBJECT_DELETED_WITH_ID("skia.objects", this->typeName(), this);
 *     }
 *     void traceSnapshot() {
 *         // The state of an object can be specified at any point with the OBJECT_SNAPSHOT macro.
 *         // This takes the "name" (actually the type name), the ID of the object (typically a
 *         // pointer), and a single (unnnamed) argument, which is the "snapshot" of that object.
 *         //
 *         // Tracing viewer requires that all object macros use the same name and id for creation,
 *         // deletion, and snapshots. However: It's convenient to put creation and deletion in the
 *         // base-class constructor/destructor where the actual type name isn't known yet. That's
 *         // what we're doing here. The JSON for snapshots can therefore include the actual type
 *         // name, and a special tag that refers to the type name originally used at creation time.
 *         // Skia's JSON tracer handles this automatically, so SNAPSHOT macros can simply use the
 *         // derived type name, and the JSON will be formatted correctly to link the events.
 *         TRACE_EVENT_OBJECT_SNAPSHOT_WITH_ID("skia.objects", this->typeName(), this,
 *                                             TRACE_STR_COPY(this->toString().c_str()));
 *     }
 *
 *     virtual const char* typeName() { return "TracingShape"; }
 *     virtual SkString toString() { return SkString("Shape()"); }
 * }
 * ```
 */
public open class TracingShape public constructor() {
  /**
   * C++ original:
   * ```cpp
   * void traceSnapshot() {
   *         // The state of an object can be specified at any point with the OBJECT_SNAPSHOT macro.
   *         // This takes the "name" (actually the type name), the ID of the object (typically a
   *         // pointer), and a single (unnnamed) argument, which is the "snapshot" of that object.
   *         //
   *         // Tracing viewer requires that all object macros use the same name and id for creation,
   *         // deletion, and snapshots. However: It's convenient to put creation and deletion in the
   *         // base-class constructor/destructor where the actual type name isn't known yet. That's
   *         // what we're doing here. The JSON for snapshots can therefore include the actual type
   *         // name, and a special tag that refers to the type name originally used at creation time.
   *         // Skia's JSON tracer handles this automatically, so SNAPSHOT macros can simply use the
   *         // derived type name, and the JSON will be formatted correctly to link the events.
   *         TRACE_EVENT_OBJECT_SNAPSHOT_WITH_ID("skia.objects", this->typeName(), this,
   *                                             TRACE_STR_COPY(this->toString().c_str()));
   *     }
   * ```
   */
  public fun traceSnapshot() {
    TODO("Implement traceSnapshot")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual const char* typeName() { return "TracingShape"; }
   * ```
   */
  public open fun typeName(): Char {
    TODO("Implement typeName")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual SkString toString() { return SkString("Shape()"); }
   * ```
   */
  public override fun toString(): String {
    TODO("Implement toString")
  }
}
