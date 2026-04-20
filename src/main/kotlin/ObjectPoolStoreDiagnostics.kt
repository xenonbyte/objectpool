package com.github.xenonbyte

/**
 * Immutable diagnostic snapshot for all pools registered in an [ObjectPoolStore].
 */
data class ObjectPoolStoreDiagnostics(
    val pools: List<ObjectPoolDiagnostics>
) {
    val poolCount: Int
        get() = pools.size

    val totalCurrentSize: Int
        get() = pools.sumOf { it.stats.currentSize }

    val totalPeakSize: Int
        get() = pools.sumOf { it.stats.peakSize }

    val totalHitCount: Long
        get() = pools.sumOf { it.stats.hitCount }

    val totalMissCount: Long
        get() = pools.sumOf { it.stats.missCount }

    val totalRecycleCount: Long
        get() = pools.sumOf { it.stats.recycleCount }

    val totalDropCount: Long
        get() = pools.sumOf { it.stats.dropCount }

    fun toDebugString(): String {
        if (pools.isEmpty()) {
            return "ObjectPoolStoreDiagnostics(poolCount=0)"
        }

        return buildString {
            appendLine(
                "ObjectPoolStoreDiagnostics(poolCount=$poolCount, totalCurrentSize=$totalCurrentSize, totalPeakSize=$totalPeakSize, totalHit=$totalHitCount, totalMiss=$totalMissCount, totalRecycle=$totalRecycleCount, totalDrop=$totalDropCount)"
            )
            pools.sortedBy { it.storageKey }.forEach { pool ->
                append(" - ")
                appendLine(pool.toDebugString())
            }
        }.trimEnd()
    }
}
