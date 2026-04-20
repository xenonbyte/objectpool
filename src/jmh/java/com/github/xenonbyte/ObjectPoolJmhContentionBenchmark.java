package com.github.xenonbyte;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class ObjectPoolJmhContentionBenchmark {
    @Benchmark
    @Threads(4)
    public void sharedBufferDirect(SharedDirectState state, Blackhole blackhole) {
        int value = state.next();
        ObjectPoolJmhSupport.BufferReusable item =
                new ObjectPoolJmhSupport.BufferReusable(
                        value, new int[ObjectPoolJmhSupport.BUFFER_SIZE]);
        blackhole.consume(ObjectPoolJmhSupport.writePayload(item, value));
    }

    @Benchmark
    @Threads(4)
    public void sharedBufferPooled(SharedPoolState state, Blackhole blackhole) {
        int value = state.next();
        ObjectPoolJmhSupport.BufferReusable item = state.pool.obtain(value);
        blackhole.consume(ObjectPoolJmhSupport.writePayload(item, value));
        state.pool.recycle(item);
    }

    @State(Scope.Benchmark)
    public static class SharedDirectState {
        private AtomicInteger sequence;

        @Setup(Level.Trial)
        public void setup() {
            sequence = new AtomicInteger();
        }

        int next() {
            return sequence.getAndIncrement();
        }
    }

    @State(Scope.Benchmark)
    public static class SharedPoolState {
        @Param({"1", "4", "16"})
        public int maxPoolSize;

        @Param({"0", "128", "4096"})
        public int resetSpan;

        private ObjectPool<ObjectPoolJmhSupport.BufferReusable> pool;
        private AtomicInteger sequence;

        @Setup(Level.Trial)
        public void setup() {
            sequence = new AtomicInteger();
            pool =
                    ObjectPoolJmhSupport.newProvider().get(
                            "jmh-shared-" + maxPoolSize + "-" + resetSpan,
                            ObjectPoolJmhSupport.BufferReusable.class,
                            ObjectPoolJmhSupport.bufferFactory(resetSpan),
                            maxPoolSize);
        }

        int next() {
            return sequence.getAndIncrement();
        }
    }
}
