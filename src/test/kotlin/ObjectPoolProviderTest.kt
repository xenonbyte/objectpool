package com.github.xenonbyte

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ObjectPoolProviderTest {

    @Test
    fun `global provider shares the same pool store`() {
        val factory = TrackingFactory(::GlobalReusable)
        val firstPool = ObjectPoolProvider.global().get(GlobalReusable::class.java, factory, maxPoolSize = 1)
        firstPool.clear()

        val created = firstPool.obtain("first")
        firstPool.recycle(created)

        val secondPool = ObjectPoolProvider.global().get(GlobalReusable::class.java, factory, maxPoolSize = 1)
        val reused = secondPool.obtain("second")

        assertSame(created, reused)
        assertEquals(1, factory.createCount)
        assertEquals(1, factory.reuseCount)

        secondPool.clear()
    }

    @Test
    fun `duplicate recycle does not enqueue the same instance twice`() {
        val factory = TrackingFactory(::DuplicateRecycleReusable)
        val pool = ObjectPoolProvider.create(localOwner()).get(
            DuplicateRecycleReusable::class.java,
            factory,
            maxPoolSize = 2
        )

        val first = pool.obtain("first")
        pool.recycle(first)
        pool.recycle(first)

        val reused = pool.obtain("reused")
        val created = pool.obtain("created")

        assertSame(first, reused)
        assertNotSame(reused, created)
        assertEquals(2, factory.createCount)
        assertEquals(1, factory.reuseCount)
    }

    @Test
    fun `negative pool size is rejected`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            ObjectPoolProvider.create(localOwner()).get(
                NegativeSizeReusable::class.java,
                TrackingFactory(::NegativeSizeReusable),
                maxPoolSize = -1
            )
        }

        assertEquals("maxPoolSize must be >= 0", exception.message)
    }

    @Test
    fun `named pools allow multiple pools for the same class`() {
        val provider = ObjectPoolProvider.create(localOwner())
        val firstFactory = TrackingFactory(::NamedPoolReusable)
        val secondFactory = TrackingFactory(::NamedPoolReusable)

        val firstPool = provider.get("first", NamedPoolReusable::class.java, firstFactory, maxPoolSize = 1)
        val secondPool = provider.get("second", NamedPoolReusable::class.java, secondFactory, maxPoolSize = 1)

        val fromFirst = firstPool.obtain("first")
        firstPool.recycle(fromFirst)

        val firstAgain = provider.get("first", NamedPoolReusable::class.java, firstFactory, maxPoolSize = 1)
        val reused = firstAgain.obtain("reused")
        val fromSecond = secondPool.obtain("second")

        assertSame(firstPool, firstAgain)
        assertSame(fromFirst, reused)
        assertNotSame(reused, fromSecond)
        assertEquals(1, firstFactory.createCount)
        assertEquals(1, firstFactory.reuseCount)
        assertEquals(1, secondFactory.createCount)
        assertEquals(0, secondFactory.reuseCount)
    }

    @Test
    fun `blank pool key is rejected`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            ObjectPoolProvider.create(localOwner()).get(
                "  ",
                NamedPoolReusable::class.java,
                TrackingFactory(::NamedPoolReusable),
                maxPoolSize = 1
            )
        }

        assertEquals("key must not be blank", exception.message)
    }

    @Test
    fun `explicit default key does not collide with implicit pool`() {
        val provider = ObjectPoolProvider.create(localOwner())
        val implicitFactory = TrackingFactory(::NamedPoolReusable)
        val explicitFactory = TrackingFactory(::NamedPoolReusable)

        val implicitPool = provider.get(
            NamedPoolReusable::class.java,
            implicitFactory,
            maxPoolSize = 1
        )
        val explicitPool = provider.get(
            "default",
            NamedPoolReusable::class.java,
            explicitFactory,
            maxPoolSize = 1
        )

        val implicitObject = implicitPool.obtain("implicit")
        implicitPool.recycle(implicitObject)
        val explicitObject = explicitPool.obtain("explicit")

        assertNotSame(implicitPool, explicitPool)
        assertNotSame(implicitObject, explicitObject)
        assertEquals(1, implicitFactory.createCount)
        assertEquals(0, implicitFactory.reuseCount)
        assertEquals(1, explicitFactory.createCount)
        assertEquals(0, explicitFactory.reuseCount)
    }

    @Test
    fun `concurrent pool lookup returns a single pool instance`() {
        val provider = ObjectPoolProvider.create(localOwner())
        val pools = CopyOnWriteArrayList<ObjectPool<ConcurrentLookupReusable>>()
        val ready = CountDownLatch(8)
        val start = CountDownLatch(1)
        val done = CountDownLatch(8)

        repeat(8) {
            thread(start = true) {
                ready.countDown()
                assertTrue(start.await(2, TimeUnit.SECONDS))
                pools += provider.get(
                    ConcurrentLookupReusable::class.java,
                    TrackingFactory(::ConcurrentLookupReusable),
                    maxPoolSize = 1
                )
                done.countDown()
            }
        }

        assertTrue(ready.await(2, TimeUnit.SECONDS))
        start.countDown()
        assertTrue(done.await(2, TimeUnit.SECONDS))
        assertEquals(1, pools.toSet().size)
    }

    private fun localOwner(): ObjectPoolStoreOwner {
        val localStore = ObjectPoolStore()
        return object : ObjectPoolStoreOwner {
            override val store: ObjectPoolStore = localStore
        }
    }

    private class TrackingFactory<T : BaseReusable>(
        private val creator: (String) -> T
    ) : ObjectFactory<T> {
        var createCount = 0
            private set
        var reuseCount = 0
            private set

        override fun create(vararg args: Any?): T {
            createCount++
            return creator(args.single() as String)
        }

        override fun reuse(instance: T, vararg args: Any?) {
            reuseCount++
            instance.value = args.single() as String
        }
    }

    private open class BaseReusable(
        var value: String
    ) : Reusable

    private class GlobalReusable(value: String) : BaseReusable(value)

    private class DuplicateRecycleReusable(value: String) : BaseReusable(value)

    private class NegativeSizeReusable(value: String) : BaseReusable(value)

    private class NamedPoolReusable(value: String) : BaseReusable(value)

    private class ConcurrentLookupReusable(value: String) : BaseReusable(value)
}
