package org.skia.foundation

/**
 * Iso-aligned port of Skia's `SkExecutor`
 * ([include/core/SkExecutor.h](https://github.com/google/skia/blob/main/include/core/SkExecutor.h)).
 *
 * Abstract work queue used by Skia's parallel rasterisation path
 * (`SkSurface::flush`, gpu pipelines, BMP/PNG decoding fan-out).
 * Real implementations upstream are FIFO / LIFO thread pools.
 *
 * **R1 status — synchronous default.** [GetDefault] returns a
 * [Synchronous] executor that runs each `add(work)` inline on the
 * calling thread. Sufficient for the GM critical path which is
 * single-threaded today; future GPU / parallel-tile work can swap
 * in a real thread pool via [SetDefault].
 */
public abstract class SkExecutor {

    /** Submit [work] to the executor. Synchronous-by-default in R1. */
    public abstract fun add(work: () -> Unit)

    /**
     * If it makes sense for this executor, use this thread to
     * execute work for a little while. R1 default: no-op.
     */
    public open fun borrow() { /* no-op */ }

    /**
     * Discard any pending work. Returns the number of work units
     * discarded. R1 default: 0 (synchronous executor has no queue).
     */
    public open fun discardAllPendingWork(): Int = 0

    public companion object {
        // Nullable to dodge a class-init cycle: `Synchronous` is a nested
        // object that extends `SkExecutor`, so referencing it from the
        // companion's static initialiser would observe a still-uninitialised
        // `Synchronous`. We resolve it lazily in `GetDefault()`.
        private var default: SkExecutor? = null

        /** Return the process-wide default executor. */
        @JvmStatic
        public fun GetDefault(): SkExecutor = default ?: Synchronous

        /**
         * Install a new process-wide default. Pass `null` to reset to the
         * built-in [Synchronous] executor. Not thread-safe (matches upstream).
         */
        @JvmStatic
        public fun SetDefault(executor: SkExecutor?) {
            default = executor
        }
    }

    /**
     * The trivial executor that runs work inline on the calling
     * thread. R1's default — covers every synchronous GM render.
     */
    public object Synchronous : SkExecutor() {
        override fun add(work: () -> Unit) {
            work()
        }
    }
}
