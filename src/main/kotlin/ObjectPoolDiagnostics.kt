package com.github.xenonbyte

import java.util.Locale

/**
 * Immutable diagnostic snapshot for a single [ObjectPool] registration.
 */
data class ObjectPoolDiagnostics(
    val storageKey: String,
    val logicalKey: String,
    val typeName: String?,
    val maxPoolSize: Int,
    val stats: ObjectPoolStats
) {
    val obtainCount: Long
        get() = stats.hitCount + stats.missCount

    val recycleAttemptCount: Long
        get() = stats.recycleCount + stats.dropCount

    val hitRate: Double
        get() = ratio(stats.hitCount, obtainCount)

    val missRate: Double
        get() = ratio(stats.missCount, obtainCount)

    val dropRate: Double
        get() = ratio(stats.dropCount, recycleAttemptCount)

    fun toDebugString(): String {
        val resolvedType = typeName ?: "unknown"
        return buildString {
            append("ObjectPoolDiagnostics(")
            append("key=").append(logicalKey)
            append(", type=").append(resolvedType)
            append(", maxPoolSize=").append(maxPoolSize)
            append(", currentSize=").append(stats.currentSize)
            append(", peakSize=").append(stats.peakSize)
            append(", hit=").append(stats.hitCount)
            append(", miss=").append(stats.missCount)
            append(", recycle=").append(stats.recycleCount)
            append(", drop=").append(stats.dropCount)
            append(", hitRate=").append(formatRate(hitRate))
            append(", dropRate=").append(formatRate(dropRate))
            append(')')
        }
    }

    private fun ratio(numerator: Long, denominator: Long): Double {
        return if (denominator == 0L) 0.0 else numerator.toDouble() / denominator.toDouble()
    }

    private fun formatRate(value: Double): String {
        return String.format(Locale.US, "%.2f%%", value * 100.0)
    }
}
