package com.github.xenonbyte

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ObjectPoolKotlinApiTest {

    @Test
    fun `reified get and objectFactory support Kotlin-first usage`() {
        val provider = ObjectPoolProvider.create(localOwner())
        var createCount = 0
        var reuseCount = 0

        val pool = provider.get<KotlinReusable>(
            factory = objectFactory(
                create = { args ->
                    createCount++
                    KotlinReusable(args[0] as String)
                },
                reuse = { instance, args ->
                    reuseCount++
                    instance.value = args[0] as String
                }
            ),
            maxPoolSize = 1
        )

        val first = pool.obtain("first")
        pool.recycle(first)
        val reused = pool.obtain("second")

        assertSame(first, reused)
        assertEquals("second", reused.value)
        assertEquals(1, createCount)
        assertEquals(1, reuseCount)
    }

    private fun localOwner(): ObjectPoolStoreOwner {
        val store = ObjectPoolStore()
        return object : ObjectPoolStoreOwner {
            override val store: ObjectPoolStore = store
        }
    }

    private class KotlinReusable(
        var value: String
    ) : Reusable
}
