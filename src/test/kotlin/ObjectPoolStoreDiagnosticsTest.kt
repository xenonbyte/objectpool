package com.github.xenonbyte

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ObjectPoolStoreDiagnosticsTest {

    @Test
    fun `store diagnostics aggregate named pools and derived rates`() {
        val store = ObjectPoolStore()
        val provider = ObjectPoolProvider.create(object : ObjectPoolStoreOwner {
            override val store: ObjectPoolStore = store
        })

        val alphaPool = provider.get(
            "alpha",
            DiagnosticReusable::class.java,
            factory = objectFactory(
                create = { args -> DiagnosticReusable(args[0] as String) },
                reuse = { instance, args -> instance.value = args[0] as String }
            ),
            maxPoolSize = 2
        )
        val betaPool = provider.get(
            "beta",
            DiagnosticReusable::class.java,
            factory = objectFactory(
                create = { args -> DiagnosticReusable(args[0] as String) },
                reuse = { instance, args -> instance.value = args[0] as String }
            ),
            maxPoolSize = 1
        )

        val alphaFirst = alphaPool.obtain("alpha-first")
        alphaPool.recycle(alphaFirst)
        alphaPool.obtain("alpha-second")

        val betaFirst = betaPool.obtain("beta-first")
        val betaSecond = betaPool.obtain("beta-second")
        betaPool.recycle(betaFirst)
        betaPool.recycle(betaSecond)

        val diagnostics = store.diagnostics()
        val poolsByKey = diagnostics.pools.associateBy { it.logicalKey }
        val alpha = requireNotNull(poolsByKey["alpha"])
        val beta = requireNotNull(poolsByKey["beta"])

        assertEquals(2, diagnostics.poolCount)
        assertEquals(1, diagnostics.totalCurrentSize)
        assertEquals(2, diagnostics.totalPeakSize)
        assertEquals(1, diagnostics.totalHitCount)
        assertEquals(3, diagnostics.totalMissCount)
        assertEquals(2, diagnostics.totalRecycleCount)
        assertEquals(1, diagnostics.totalDropCount)

        assertEquals(DiagnosticReusable::class.java.name, alpha.typeName)
        assertEquals(2, alpha.maxPoolSize)
        assertEquals(0, alpha.stats.currentSize)
        assertEquals(1, alpha.stats.hitCount)
        assertEquals(1, alpha.stats.missCount)
        assertEquals(1, alpha.stats.recycleCount)
        assertEquals(0, alpha.stats.dropCount)
        assertEquals(0.5, alpha.hitRate)
        assertEquals(0.0, alpha.dropRate)

        assertEquals(1, beta.maxPoolSize)
        assertEquals(1, beta.stats.currentSize)
        assertEquals(0, beta.stats.hitCount)
        assertEquals(2, beta.stats.missCount)
        assertEquals(1, beta.stats.recycleCount)
        assertEquals(1, beta.stats.dropCount)
        assertEquals(0.0, beta.hitRate)
        assertEquals(0.5, beta.dropRate)

        assertTrue(alpha.toDebugString().contains("key=alpha"))
        assertTrue(alpha.toDebugString().contains("hitRate=50.00%"))
        assertTrue(diagnostics.toDebugString().contains("poolCount=2"))
        assertTrue(diagnostics.toDebugString().contains("key=beta"))
    }

    private class DiagnosticReusable(
        var value: String
    ) : Reusable
}
