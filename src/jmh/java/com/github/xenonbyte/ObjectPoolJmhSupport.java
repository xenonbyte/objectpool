package com.github.xenonbyte;

import java.util.Arrays;

final class ObjectPoolJmhSupport {
    static final int BUFFER_SIZE = 4096;

    private ObjectPoolJmhSupport() {}

    static ObjectPoolProvider newProvider() {
        return ObjectPoolProvider.create(
                new ObjectPoolStoreOwner() {
                    private final ObjectPoolStore store = new ObjectPoolStore();

                    @Override
                    public ObjectPoolStore getStore() {
                        return store;
                    }
                });
    }

    static ObjectFactory<SmallReusable> smallFactory() {
        return new ObjectFactory<SmallReusable>() {
            @Override
            public SmallReusable create(Object... args) {
                return new SmallReusable((Long) args[0], (Integer) args[1]);
            }

            @Override
            public void reuse(SmallReusable instance, Object... args) {
                instance.id = (Long) args[0];
                instance.tag = (Integer) args[1];
            }
        };
    }

    static ObjectFactory<BufferReusable> bufferFactory(int resetSpan) {
        return new ObjectFactory<BufferReusable>() {
            @Override
            public BufferReusable create(Object... args) {
                return new BufferReusable((Integer) args[0], new int[BUFFER_SIZE]);
            }

            @Override
            public void reuse(BufferReusable instance, Object... args) {
                instance.length = (Integer) args[0];
                if (resetSpan > 0) {
                    Arrays.fill(instance.payload, 0, Math.min(resetSpan, instance.payload.length), 0);
                }
            }
        };
    }

    static long writePayload(BufferReusable item, int seed) {
        item.payload[0] = seed;
        item.payload[item.payload.length / 2] = item.length;
        item.payload[item.payload.length - 1] =
                item.payload[0] ^ item.payload[item.payload.length / 2];
        return (long) item.payload[0]
                + item.payload[item.payload.length / 2]
                + item.payload[item.payload.length - 1];
    }

    static final class SmallReusable implements Reusable {
        long id;
        int tag;

        SmallReusable(long id, int tag) {
            this.id = id;
            this.tag = tag;
        }
    }

    static final class BufferReusable implements Reusable {
        int length;
        final int[] payload;

        BufferReusable(int length, int[] payload) {
            this.length = length;
            this.payload = payload;
        }
    }
}
