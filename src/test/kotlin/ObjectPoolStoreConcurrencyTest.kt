package com.github.xenonbyte

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ObjectPoolStoreConcurrencyTest {

    @Test
    fun `clear waits for in-flight registration and clears the newly registered pool`() {
        val store = ObjectPoolStore()
        val factoryStarted = CountDownLatch(1)
        val releaseFactory = CountDownLatch(1)
        val clearFinished = CountDownLatch(1)
        var returnedPool: ObjectPool<StoreReusable>? = null

        val registrationThread = thread(start = true) {
            returnedPool = store.getOrPut(
                storageKey = "storage",
                logicalKey = "logical",
                typeName = StoreReusable::class.java.name,
                maxPoolSize = 1
            ) {
                factoryStarted.countDown()
                assertTrue(releaseFactory.await(2, TimeUnit.SECONDS))

                ObjectPool(
                    objectFactory(
                        create = { args -> StoreReusable(args[0] as String) },
                        reuse = { instance, args -> instance.value = args[0] as String }
                    ),
                    maxPoolSize = 1
                ).also { pool ->
                    pool.recycle(StoreReusable("seed"))
                }
            }
        }

        assertTrue(factoryStarted.await(2, TimeUnit.SECONDS))
        thread(start = true) {
            store.clear()
            clearFinished.countDown()
        }

        assertFalse(clearFinished.await(200, TimeUnit.MILLISECONDS))
        releaseFactory.countDown()
        registrationThread.join()
        assertTrue(clearFinished.await(2, TimeUnit.SECONDS))

        val pool = assertNotNull(returnedPool)
        assertNull(store.get<StoreReusable>("storage"))
        assertEquals(0, pool.stats().currentSize)
    }

    private class StoreReusable(
        var value: String
    ) : Reusable
}
