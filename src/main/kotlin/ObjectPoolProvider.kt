package com.github.xenonbyte

/**
 * Provides the program class for ObjectPool
 *
 * @param Owner The owner of the pool storage, responsible for storing and retrieving the object pool.
 *
 * @see ObjectPoolStore
 * @see ObjectPool
 * @see ObjectFactory
 * @author xubo
 */
class ObjectPoolProvider private constructor(
    owner: ObjectPoolStoreOwner
) {
    private val store = owner.store

    companion object {
        const val DEFAULT_POOL_MAX_SIZE = 5
        private const val POOL_KEY_PREFIX = "xenon_byte.pool"
        private const val IMPLICIT_POOL_LOGICAL_KEY = "(default)"
        private val GLOBAL_POOL_STORE = ObjectPoolStore()
        private val GLOBAL_POOL_STORE_OWNER = object : ObjectPoolStoreOwner {
            override val store: ObjectPoolStore
                get() = GLOBAL_POOL_STORE

        }

        /**
         * create an instance of [ObjectPoolProvider].
         * @param owner The owner that holds the pool store.
         * @return An instance of [ObjectPoolProvider].
         */
        @JvmStatic
        fun create(owner: ObjectPoolStoreOwner): ObjectPoolProvider {
            return ObjectPoolProvider(owner)
        }

        /**
         * Create a global instance of [ObjectPoolProvider].
         * @param Owner The owner of the pool storage.
         * @return An instance of [ObjectPoolProvider].
         */
        @JvmStatic
        fun global(): ObjectPoolProvider {
            return create(GLOBAL_POOL_STORE_OWNER)
        }
    }

    /**
     * Retrieves an object pool for a specific object type. If the pool does not exist in the store,
     * it creates a new pool using the provided factory and stores it.
     *
     * @param clazz The class of the object type [T] that the pool manages.
     * @param factory The factory used to create objects of type [T].
     * @return The object pool for the given object type.
     */
    fun <T : Reusable> get(
        clazz: Class<T>,
        factory: ObjectFactory<T>
    ): ObjectPool<T> {
        return get(clazz, factory, DEFAULT_POOL_MAX_SIZE)
    }

    /**
     * Retrieves an object pool for a specific object type. If the pool does not exist in the store,
     * it creates a new pool using the provided factory and stores it.
     *
     * @param clazz The class of the object type [T] that the pool manages.
     * @param factory The factory used to create objects of type [T].
     * @param maxPoolSize The maximum size of the pool. Defaults to [ObjectPoolProvider.DEFAULT_POOL_MAX_SIZE].
     * @return The object pool for the given object type.
     */
    fun <T : Reusable> get(
        clazz: Class<T>,
        factory: ObjectFactory<T>,
        maxPoolSize: Int = DEFAULT_POOL_MAX_SIZE
    ): ObjectPool<T> {
        val poolKey = buildImplicitPoolKey(clazz)
        return store.getOrPut(
            storageKey = poolKey,
            logicalKey = IMPLICIT_POOL_LOGICAL_KEY,
            typeName = clazz.name,
            maxPoolSize = maxPoolSize
        ) {
            createObjectPool(factory, maxPoolSize)
        }
    }

    /**
     * Retrieves a named object pool for a specific object type.
     * Different [key] values allow multiple pools to coexist for the same class inside one store.
     *
     * @param key The logical name of the pool within the current store.
     * @param clazz The class of the object type [T] that the pool manages.
     * @param factory The factory used to create objects of type [T].
     * @return The object pool for the given key and object type.
     */
    fun <T : Reusable> get(
        key: String,
        clazz: Class<T>,
        factory: ObjectFactory<T>
    ): ObjectPool<T> {
        return get(key, clazz, factory, DEFAULT_POOL_MAX_SIZE)
    }

    /**
     * Retrieves a named object pool for a specific object type.
     * Different [key] values allow multiple pools to coexist for the same class inside one store.
     *
     * @param key The logical name of the pool within the current store.
     * @param clazz The class of the object type [T] that the pool manages.
     * @param factory The factory used to create objects of type [T].
     * @param maxPoolSize The maximum size of the pool. Defaults to [ObjectPoolProvider.DEFAULT_POOL_MAX_SIZE].
     * @return The object pool for the given key and object type.
     */
    fun <T : Reusable> get(
        key: String,
        clazz: Class<T>,
        factory: ObjectFactory<T>,
        maxPoolSize: Int = DEFAULT_POOL_MAX_SIZE
    ): ObjectPool<T> {
        require(key.isNotBlank()) { "key must not be blank" }

        val poolKey = buildPoolKey(key, clazz)
        return store.getOrPut(
            storageKey = poolKey,
            logicalKey = key,
            typeName = clazz.name,
            maxPoolSize = maxPoolSize
        ) {
            createObjectPool(factory, maxPoolSize)
        }
    }

    /**
     * Create a new object pool using a factory.
     *
     * @param factory The factory used to create objects of type [T].
     * @param maxPoolSize The maximum size of the pool.
     * @return The newly created object pool.
     */
    private fun <T : Reusable> createObjectPool(factory: ObjectFactory<T>, maxPoolSize: Int): ObjectPool<T> {
        return ObjectPool(factory, maxPoolSize)
    }

    private fun buildPoolKey(key: String, clazz: Class<*>): String {
        return "$POOL_KEY_PREFIX:named:$key:${clazz.name}"
    }

    private fun buildImplicitPoolKey(clazz: Class<*>): String {
        return "$POOL_KEY_PREFIX:implicit:${clazz.name}"
    }
}
