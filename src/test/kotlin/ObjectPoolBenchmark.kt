package com.github.xenonbyte

import kotlin.system.measureNanoTime

private const val WARMUP_ROUNDS = 3
private const val MEASURE_ROUNDS = 5
private const val SMALL_OBJECT_ITERATIONS = 1_000_000
private const val BUFFER_OBJECT_ITERATIONS = 250_000
private const val BUFFER_SIZE = 4096

@Volatile
private var sink: Long = 0

fun main() {
    println("ObjectPool local benchmark")
    println("Warmup rounds: $WARMUP_ROUNDS, measure rounds: $MEASURE_ROUNDS")
    println()

    runScenario(
        name = "small-object",
        iterations = SMALL_OBJECT_ITERATIONS,
        direct = ::runDirectSmallObject,
        pooled = ::runPooledSmallObject
    )

    println()

    runScenario(
        name = "buffer-object",
        iterations = BUFFER_OBJECT_ITERATIONS,
        direct = ::runDirectBufferObject,
        pooled = ::runPooledBufferObject
    )
}

private fun runScenario(
    name: String,
    iterations: Int,
    direct: (Int) -> Long,
    pooled: (Int) -> Long
) {
    repeat(WARMUP_ROUNDS) {
        direct(iterations / 10)
        pooled(iterations / 10)
    }

    val directNanos = benchmark(iterations, direct)
    val pooledNanos = benchmark(iterations, pooled)
    report(name, iterations, directNanos, pooledNanos)
}

private fun benchmark(
    iterations: Int,
    block: (Int) -> Long
): Long {
    var best = Long.MAX_VALUE
    repeat(MEASURE_ROUNDS) {
        val elapsed = measureNanoTime {
            sink = block(iterations)
        }
        best = minOf(best, elapsed)
    }
    return best
}

private fun report(
    name: String,
    iterations: Int,
    directNanos: Long,
    pooledNanos: Long
) {
    val directOpsPerSec = iterations * 1_000_000_000.0 / directNanos
    val pooledOpsPerSec = iterations * 1_000_000_000.0 / pooledNanos
    val speedup = directNanos.toDouble() / pooledNanos.toDouble()

    println("Scenario: $name")
    println("  direct : ${"%.2f".format(directOpsPerSec)} ops/s (${directNanos / 1_000_000.0} ms best)")
    println("  pooled : ${"%.2f".format(pooledOpsPerSec)} ops/s (${pooledNanos / 1_000_000.0} ms best)")
    println("  ratio  : ${"%.2f".format(speedup)}x (${if (speedup >= 1.0) "pooled faster" else "direct faster"})")
}

private fun runDirectSmallObject(iterations: Int): Long {
    var checksum = 0L
    repeat(iterations) { index ->
        val item = SmallReusable(index.toLong(), index and 31)
        checksum += item.id + item.tag
    }
    return checksum
}

private fun runPooledSmallObject(iterations: Int): Long {
    val pool = ObjectPoolProvider.create(localOwner()).get(
        "benchmark-small",
        SmallReusable::class.java,
        object : ObjectFactory<SmallReusable> {
            override fun create(vararg args: Any?): SmallReusable {
                return SmallReusable(
                    id = args[0] as Long,
                    tag = args[1] as Int
                )
            }

            override fun reuse(instance: SmallReusable, vararg args: Any?) {
                instance.id = args[0] as Long
                instance.tag = args[1] as Int
            }
        },
        maxPoolSize = 1
    )

    var checksum = 0L
    repeat(iterations) { index ->
        val item = pool.obtain(index.toLong(), index and 31)
        checksum += item.id + item.tag
        pool.recycle(item)
    }
    return checksum
}

private fun runDirectBufferObject(iterations: Int): Long {
    var checksum = 0L
    repeat(iterations) { index ->
        val item = BufferReusable(index, IntArray(BUFFER_SIZE))
        checksum += writeBufferPayload(item, index)
    }
    return checksum
}

private fun runPooledBufferObject(iterations: Int): Long {
    val pool = ObjectPoolProvider.create(localOwner()).get(
        "benchmark-buffer",
        BufferReusable::class.java,
        object : ObjectFactory<BufferReusable> {
            override fun create(vararg args: Any?): BufferReusable {
                return BufferReusable(
                    length = args[0] as Int,
                    payload = IntArray(BUFFER_SIZE)
                )
            }

            override fun reuse(instance: BufferReusable, vararg args: Any?) {
                instance.length = args[0] as Int
            }
        },
        maxPoolSize = 1
    )

    var checksum = 0L
    repeat(iterations) { index ->
        val item = pool.obtain(index)
        checksum += writeBufferPayload(item, index)
        pool.recycle(item)
    }
    return checksum
}

private fun writeBufferPayload(
    item: BufferReusable,
    seed: Int
): Long {
    item.payload[0] = seed
    item.payload[item.payload.lastIndex / 2] = item.length
    item.payload[item.payload.lastIndex] = item.payload[0] xor item.payload[item.payload.lastIndex / 2]
    return item.payload[0].toLong() + item.payload[item.payload.lastIndex / 2] + item.payload[item.payload.lastIndex]
}

private fun localOwner(): ObjectPoolStoreOwner {
    val store = ObjectPoolStore()
    return object : ObjectPoolStoreOwner {
        override val store: ObjectPoolStore = store
    }
}

private class SmallReusable(
    var id: Long,
    var tag: Int
) : Reusable

private class BufferReusable(
    var length: Int,
    val payload: IntArray
) : Reusable
