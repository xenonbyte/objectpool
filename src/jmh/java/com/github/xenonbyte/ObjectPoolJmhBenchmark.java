package com.github.xenonbyte;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class ObjectPoolJmhBenchmark {
    @Benchmark
    public void smallObjectDirect(SmallDirectState state, Blackhole blackhole) {
        int value = state.next();
        ObjectPoolJmhSupport.SmallReusable item =
                new ObjectPoolJmhSupport.SmallReusable(value, value & 31);
        blackhole.consume(item.id);
        blackhole.consume(item.tag);
    }

    @Benchmark
    public void smallObjectPooled(SmallPoolState state, Blackhole blackhole) {
        int value = state.next();
        ObjectPoolJmhSupport.SmallReusable item = state.pool.obtain((long) value, value & 31);
        blackhole.consume(item.id);
        blackhole.consume(item.tag);
        state.pool.recycle(item);
    }

    @Benchmark
    public void bufferObjectDirect(BufferDirectState state, Blackhole blackhole) {
        int value = state.next();
        ObjectPoolJmhSupport.BufferReusable item =
                new ObjectPoolJmhSupport.BufferReusable(value, new int[ObjectPoolJmhSupport.BUFFER_SIZE]);
        blackhole.consume(ObjectPoolJmhSupport.writePayload(item, value));
    }

    @Benchmark
    public void bufferObjectPooled(BufferPoolState state, Blackhole blackhole) {
        int value = state.next();
        ObjectPoolJmhSupport.BufferReusable item = state.pool.obtain(value);
        blackhole.consume(ObjectPoolJmhSupport.writePayload(item, value));
        state.pool.recycle(item);
    }

    @State(Scope.Thread)
    public static class SmallDirectState {
        private int sequence;

        public int next() {
            return sequence++;
        }
    }

    @State(Scope.Thread)
    public static class SmallPoolState {
        private ObjectPool<ObjectPoolJmhSupport.SmallReusable> pool;
        private int sequence;

        @Setup(Level.Trial)
        public void setup() {
            pool =
                    ObjectPoolJmhSupport.newProvider().get(
                            "jmh-small",
                            ObjectPoolJmhSupport.SmallReusable.class,
                            ObjectPoolJmhSupport.smallFactory(),
                            1);
        }

        public int next() {
            return sequence++;
        }
    }

    @State(Scope.Thread)
    public static class BufferDirectState {
        private int sequence;

        public int next() {
            return sequence++;
        }
    }

    @State(Scope.Thread)
    public static class BufferPoolState {
        private ObjectPool<ObjectPoolJmhSupport.BufferReusable> pool;
        private int sequence;

        @Setup(Level.Trial)
        public void setup() {
            pool =
                    ObjectPoolJmhSupport.newProvider().get(
                            "jmh-buffer",
                            ObjectPoolJmhSupport.BufferReusable.class,
                            ObjectPoolJmhSupport.bufferFactory(0),
                            1);
        }

        public int next() {
            return sequence++;
        }
    }
}
