package com.github.xenonbyte

/**
 * Kotlin-friendly typed overload that infers the pooled class from [T].
 */
inline fun <reified T : Reusable> ObjectPoolProvider.get(
    factory: ObjectFactory<T>,
    maxPoolSize: Int = ObjectPoolProvider.DEFAULT_POOL_MAX_SIZE
): ObjectPool<T> {
    return get(T::class.java, factory, maxPoolSize)
}

/**
 * Kotlin-friendly typed overload that infers the pooled class from [T].
 * Different [key] values allow multiple pools for the same type inside one store.
 */
inline fun <reified T : Reusable> ObjectPoolProvider.get(
    key: String,
    factory: ObjectFactory<T>,
    maxPoolSize: Int = ObjectPoolProvider.DEFAULT_POOL_MAX_SIZE
): ObjectPool<T> {
    return get(key, T::class.java, factory, maxPoolSize)
}

/**
 * Builds an [ObjectFactory] from Kotlin lambdas to avoid verbose anonymous objects.
 */
inline fun <T : Reusable> objectFactory(
    crossinline create: (Array<out Any?>) -> T,
    crossinline reuse: (instance: T, args: Array<out Any?>) -> Unit
): ObjectFactory<T> {
    return object : ObjectFactory<T> {
        override fun create(vararg args: Any?): T {
            return create(args)
        }

        override fun reuse(instance: T, vararg args: Any?) {
            reuse(instance, args)
        }
    }
}
