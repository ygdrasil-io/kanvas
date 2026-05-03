package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlinx.atomicfu.AtomicBoolean
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class SkIDChangeListener : public SkRefCnt {
 * public:
 *     SkIDChangeListener();
 *
 *     ~SkIDChangeListener() override;
 *
 *     virtual void changed() = 0;
 *
 *     /**
 *      * Mark the listener is no longer needed. It should be removed and changed() should not be
 *      * called.
 *      */
 *     void markShouldDeregister() { fShouldDeregister.store(true, std::memory_order_relaxed); }
 *
 *     /** Indicates whether markShouldDeregister was called. */
 *     bool shouldDeregister() { return fShouldDeregister.load(std::memory_order_acquire); }
 *
 *     /** Manages a list of SkIDChangeListeners. */
 *     class List {
 *     public:
 *         List();
 *
 *         ~List();
 *
 *         /**
 *          * Add a new listener to the list. It must not already be deregistered. Also clears out
 *          * previously deregistered listeners.
 *          */
 *         void add(sk_sp<SkIDChangeListener> listener) SK_EXCLUDES(fMutex);
 *
 *         /**
 *          * The number of registered listeners (including deregisterd listeners that are yet-to-be
 *          * removed.
 *          */
 *         int count() const SK_EXCLUDES(fMutex);
 *
 *         /** Calls changed() on all listeners that haven't been deregistered and resets the list. */
 *         void changed() SK_EXCLUDES(fMutex);
 *
 *         /** Resets without calling changed() on the listeners. */
 *         void reset() SK_EXCLUDES(fMutex);
 *
 *     private:
 *         mutable SkMutex fMutex;
 *         skia_private::STArray<1, sk_sp<SkIDChangeListener>> fListeners SK_GUARDED_BY(fMutex);
 *     };
 *
 * private:
 *     std::atomic<bool> fShouldDeregister;
 * }
 * ```
 */
public abstract class SkIDChangeListener public constructor() : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * std::atomic<bool> fShouldDeregister
   * ```
   */
  private val fShouldDeregister: AtomicBoolean = TODO("Initialize fShouldDeregister")

  /**
   * C++ original:
   * ```cpp
   * virtual void changed() = 0
   * ```
   */
  public abstract fun changed()

  /**
   * C++ original:
   * ```cpp
   * void markShouldDeregister() { fShouldDeregister.store(true, std::memory_order_relaxed); }
   * ```
   */
  public fun markShouldDeregister() {
    TODO("Implement markShouldDeregister")
  }

  /**
   * C++ original:
   * ```cpp
   * bool shouldDeregister() { return fShouldDeregister.load(std::memory_order_acquire); }
   * ```
   */
  public fun shouldDeregister(): Boolean {
    TODO("Implement shouldDeregister")
  }

  public open class List public constructor() {
    private var fMutex: Int = TODO("Initialize fMutex")

    private var fListeners: Int = TODO("Initialize fListeners")

    public fun add(listener: SkSp<SkIDChangeListener>) {
      TODO("Implement add")
    }

    public fun count(): Int {
      TODO("Implement count")
    }

    public fun changed() {
      TODO("Implement changed")
    }

    public fun reset() {
      TODO("Implement reset")
    }
  }
}
