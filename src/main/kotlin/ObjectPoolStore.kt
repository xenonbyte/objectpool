package com.github.xenonbyte

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * A store that holds and manages object pools.
 *
 *
 * @see ObjectPool
 * @author xubo
 */
class ObjectPoolStore {
    private val poolMap = mutableMapOf<String, PoolEntry>()
    private val lock = ReentrantReadWriteLock()

    /**
     * Retrieves an object pool associated with the given key from the store.
     *
     * @param key The key associated with the object pool.
     * @return The object pool for the specified key, or null if the pool does not exist.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Reusable> get(key: String): ObjectPool<T>? {
        return lock.read {
            poolMap[key]?.pool as ObjectPool<T>?
        }
    }

    /**
     * Stores the provided object pool in the store, associated with the given key.
     *
     * @param key The key to associate with the object pool.
     * @param pool The object pool to be stored.
     */
    fun <T : Reusable> put(key: String, pool: ObjectPool<T>) {
        val oldEntry = lock.write {
            poolMap.put(
                key,
                PoolEntry(
                    storageKey = key,
                    logicalKey = key,
                    typeName = null,
                    maxPoolSize = pool.maxPoolSize,
                    pool = pool
                )
            )
        }
        if (oldEntry?.pool !== pool) {
            oldEntry?.pool?.clear()
        }
    }

    /**
     * Clears all the object pools stored in the store.
     * This will release any objects that are held in the pools and free up resources.
     */
    fun clear() {
        val pools = lock.write {
            val currentPools = poolMap.values.map { it.pool }
            poolMap.clear()
            currentPools
        }
        for (pool in pools) {
            pool.clear()
        }
    }

    /**
     * Returns a store-wide immutable diagnostics snapshot for all registered pools.
     */
    fun diagnostics(): ObjectPoolStoreDiagnostics {
        val entries = lock.read {
            poolMap.values.toList()
        }
        val pools = entries.map { entry ->
            ObjectPoolDiagnostics(
                storageKey = entry.storageKey,
                logicalKey = entry.logicalKey,
                typeName = entry.typeName,
                maxPoolSize = entry.maxPoolSize,
                stats = entry.pool.stats()
            )
        }
        return ObjectPoolStoreDiagnostics(pools)
    }

    @Suppress("UNCHECKED_CAST")
    internal fun <T : Reusable> getOrPut(
        storageKey: String,
        logicalKey: String,
        typeName: String,
        maxPoolSize: Int,
        poolFactory: () -> ObjectPool<T>
    ): ObjectPool<T> {
        return lock.write {
            val existing = poolMap[storageKey]
            if (existing != null) {
                return@write existing.pool as ObjectPool<T>
            }

            val newPool = poolFactory()
            poolMap[storageKey] = PoolEntry(
                storageKey = storageKey,
                logicalKey = logicalKey,
                typeName = typeName,
                maxPoolSize = maxPoolSize,
                pool = newPool
            )
            newPool
        }
    }

    private data class PoolEntry(
        val storageKey: String,
        val logicalKey: String,
        val typeName: String?,
        val maxPoolSize: Int,
        val pool: ObjectPool<*>
    )
}
