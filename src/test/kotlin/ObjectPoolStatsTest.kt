package com.github.xenonbyte

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ObjectPoolStatsTest {

    @Test
    fun `stats track hit miss recycle drop and peak size`() {
        val pool = ObjectPoolProvider.create(localOwner()).get(
            StatsReusable::class.java,
            factory = objectFactory(
                create = { args -> StatsReusable(args[0] as String) },
                reuse = { instance, args -> instance.value = args[0] as String }
            ),
            maxPoolSize = 2
        )

        val first = pool.obtain("first")
        val second = pool.obtain("second")

        pool.recycle(first)
        pool.recycle(second)

        val reused = pool.obtain("reused")
        pool.recycle(reused)
        pool.recycle(reused)

        val stats = pool.stats()

        assertSame(second, reused)
        assertEquals(
            ObjectPoolStats(
                hitCount = 1,
                missCount = 2,
                recycleCount = 3,
                dropCount = 1,
                currentSize = 2,
                peakSize = 2
            ),
            stats
        )
    }

    @Test
    fun `stats keep cumulative counters after clear but reset current size`() {
        val pool = ObjectPoolProvider.create(localOwner()).get(
            StatsReusable::class.java,
            factory = objectFactory(
                create = { args -> StatsReusable(args[0] as String) },
                reuse = { instance, args -> instance.value = args[0] as String }
            ),
            maxPoolSize = 1
        )

        val first = pool.obtain("first")
        val second = pool.obtain("second")
        pool.recycle(first)
        pool.recycle(second)
        pool.clear()

        val stats = pool.stats()

        assertEquals(0, stats.hitCount)
        assertEquals(2, stats.missCount)
        assertEquals(1, stats.recycleCount)
        assertEquals(1, stats.dropCount)
        assertEquals(0, stats.currentSize)
        assertEquals(1, stats.peakSize)
    }

    private fun localOwner(): ObjectPoolStoreOwner {
        val store = ObjectPoolStore()
        return object : ObjectPoolStoreOwner {
            override val store: ObjectPoolStore = store
        }
    }

    private class StatsReusable(
        var value: String
    ) : Reusable
}
