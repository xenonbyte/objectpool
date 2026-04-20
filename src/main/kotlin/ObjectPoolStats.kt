package com.github.xenonbyte

/**
 * Immutable runtime statistics snapshot for an [ObjectPool].
 *
 * @property hitCount Number of obtain calls served from the pool.
 * @property missCount Number of obtain calls that had to create a new object.
 * @property recycleCount Number of recycle calls accepted by the pool.
 * @property dropCount Number of recycle calls dropped because the pool was full or the instance was already pooled.
 * @property currentSize Current number of instances stored in the pool.
 * @property peakSize Highest [currentSize] observed since the pool was created.
 */
data class ObjectPoolStats(
    val hitCount: Long,
    val missCount: Long,
    val recycleCount: Long,
    val dropCount: Long,
    val currentSize: Int,
    val peakSize: Int
)
