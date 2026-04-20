package com.github.xenonbyte

import java.util.ArrayDeque
import java.util.IdentityHashMap

/**
 * A generic object pool that can manage and reuse instances of objects.
 *
 * This class is typically used in scenarios where objects are frequently created and destroyed.
 * Reusing object instances can help improve performance and memory efficiency, especially in resource-constrained environments.
 *
 * It allows for the efficient reuse of objects by maintaining a pool of instances that can be reused rather than creating new ones each time.
 * Objects that are reusable should implement the [com.github.xenonbyte.Reusable] interface.
 *
 * The pool has a maximum size (controlled by [maxPoolSize]),If the pool exceeds the maximum size, it will discard objects directly instead of saving them back.
 *
 * @param T The type of the objects that can be managed by this pool. Must be a subtype of [com.github.xenonbyte.Reusable].
 * @param maxPoolSize The maximum number of objects the pool can hold. Default is [ObjectPoolProvider.DEFAULT_POOL_MAX_SIZE].
 *
 * @see com.github.xenonbyte.Reusable
 * @author xubo
 */
class ObjectPool<T : Reusable> internal constructor(
    private val objectFactory: ObjectFactory<T>,
    internal val maxPoolSize: Int
) {
    private val pool = ArrayDeque<T>(maxPoolSize.coerceAtLeast(0))
    private val pooledObjects = IdentityHashMap<T, Unit>()
    private val lock = Any()
    private var hitCount = 0L
    private var missCount = 0L
    private var recycleCount = 0L
    private var dropCount = 0L
    private var peakSize = 0

    init {
        require(maxPoolSize >= 0) { "maxPoolSize must be >= 0" }
    }

    /**
     * Obtain an object from the pool. If the pool has a reusable object, it will be returned and reused.
     * Otherwise, a new object will be created.
     *
     * @param args Arguments used for reusing or creating a new object.
     * @return A reusable or newly created object of type [T].
     */
    fun obtain(vararg args: Any?): T {
        val reusable = synchronized(lock) {
            if (pool.isEmpty()) {
                missCount++
                null
            } else {
                pool.removeLast().also {
                    pooledObjects.remove(it)
                    hitCount++
                }
            }
        }

        return reusable?.also {
            // Factory hooks may execute arbitrary user code, so keep them outside the pool lock.
            objectFactory.reuse(it, *args)
        } ?: objectFactory.create(*args)
    }

    /**
     * Recycle the given object back into the pool. If the pool size is less than the maximum size,
     * the object will be added to the pool. Otherwise, it will be discarded.
     *
     * @param recycleObject The object to be recycled back into the pool.
     */
    fun recycle(recycleObject: T) {
        synchronized(lock) {
            if (pool.size >= maxPoolSize || pooledObjects.containsKey(recycleObject)) {
                dropCount++
                return
            }

            pooledObjects[recycleObject] = Unit
            pool.addLast(recycleObject)
            recycleCount++
            peakSize = maxOf(peakSize, pool.size)
        }
    }

    fun clear() {
        synchronized(lock) {
            pool.clear()
            pooledObjects.clear()
        }
    }

    /**
     * Returns an immutable snapshot of the pool's runtime counters.
     */
    fun stats(): ObjectPoolStats {
        return synchronized(lock) {
            ObjectPoolStats(
                hitCount = hitCount,
                missCount = missCount,
                recycleCount = recycleCount,
                dropCount = dropCount,
                currentSize = pool.size,
                peakSize = peakSize
            )
        }
    }
}
