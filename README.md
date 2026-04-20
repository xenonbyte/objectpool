# ObjectPool

An object reuse pool suitable for scenes where objects are frequently created and destroyed. If an instance exists in the reuse pool, the instance is reused; if not, a new instance is created.

[中文说明](README-zh.md)

## Use

### creating a global object reuse pool
```kotlin
val pool = ObjectPoolProvider.global().get(Person::class.java, object : ObjectFactory<Person> {
    override fun create(vararg args: Any?): Person {
        val name = args[0] as String
        val age = args[1] as Int
        return Person(name, age)
    }

    override fun reuse(instance: Person, vararg args: Any?) {
        val name = args[0] as String
        val age = args[1] as Int
        instance.name = name
        instance.age = age
    }

})
```

### Kotlin concise usage

```kotlin
val pool = ObjectPoolProvider.global().get<Person>(
    factory = objectFactory(
        create = { args -> Person(args[0] as String, args[1] as Int) },
        reuse = { instance, args ->
            instance.name = args[0] as String
            instance.age = args[1] as Int
        }
    ),
    maxPoolSize = 8
)
```

### use `ObjectPoolStoreOwner` instance to create an object reuse pool
```kotlin
val store = ObjectPoolStore()
val storeOwner = object : ObjectPoolStoreOwner {
    override val store: ObjectPoolStore
        get() = store

}
val pool = ObjectPoolProvider.create(storeOwner).get(Person::class.java, object : ObjectFactory<Person> {
    override fun create(vararg args: Any?): Person {
        val name = args[0] as String
        val age = args[1] as Int
        return Person(name, age)
    }

    override fun reuse(instance: Person, vararg args: Any?) {
        val name = args[0] as String
        val age = args[1] as Int
        instance.name = name
        instance.age = age
    }

})
```

### create multiple pools for the same type
Use a custom key when one class needs isolated pools with different semantics.
```kotlin
val fastPool = ObjectPoolProvider.global().get(
    "fast-path",
    Person::class.java,
    personFactory,
    maxPoolSize = 8
)
val ioPool = ObjectPoolProvider.global().get(
    "io-path",
    Person::class.java,
    personFactory,
    maxPoolSize = 2
)
```

### get instance
get the object instance from the object reuse pool. If the instance exists in the reuse pool, reuse the instance; if not, create a new instance
```kotlin
val person = pool.obtain("Andy", 16);
```
### recycle instance
the object reuse pool recycles objects to facilitate instance reuse
```kotlin
pool.recycle(person)
```

Do not recycle the same live instance twice. Duplicate recycle calls are ignored to avoid returning the same object to multiple callers.

### inspect pool statistics

Use `stats()` to read an immutable runtime snapshot:

```kotlin
val stats = pool.stats()
println(stats.hitCount)
println(stats.missCount)
println(stats.dropCount)
```

Recommended interpretation:
- high `hitCount` means the pool is actually being reused
- persistently high `missCount` suggests the pool may not be worth keeping
- high `dropCount` suggests `maxPoolSize` is too small or callers are recycling the same instance twice
- `peakSize` close to `maxPoolSize` suggests the pool is saturating
- `currentSize` shows how many instances are retained right now

### inspect the whole store

If you manage multiple pools, export a store-wide diagnostics snapshot:

```kotlin
val diagnostics = store.diagnostics()
println(diagnostics.toDebugString())
```

Each pool entry includes:
- `logicalKey`
- `typeName`
- `maxPoolSize`
- `stats`
- `hitRate`
- `dropRate`

### implementing the `Reusable` interface
Object classes managed by the object reuse pool must implement `Reusable`
```kotlin
class Person(var name: String, var age: Int) : Reusable
```

## Benchmark
Run the local benchmark task for a quick baseline between direct allocation and pooled allocation:

```bash
./gradlew benchmarkObjectPool
```

This benchmark is dependency-free and intended as a quick local baseline rather than a replacement for JMH.

Run the formal JMH microbenchmarks with:

```bash
./gradlew jmh
```

The current JMH suite covers:
- `smallObjectDirect` / `smallObjectPooled`
- `bufferObjectDirect` / `bufferObjectPooled`
- `sharedBufferDirect` / `sharedBufferPooled`

These scenarios cover:
- the difference between tiny objects and allocation-heavy objects with large backing buffers
- the impact of `maxPoolSize` and `resetSpan` under 4-thread shared-pool contention

The repository uses development-friendly JMH defaults:
- `warmupIterations = 1`
- `iterations = 2`
- `warmup = 1s`
- `timeOnIteration = 2s`

Increase them when you need publication-grade benchmark numbers.


## Download
```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.xenonbyte:ObjectPool:2.0.0'
}
```

## Release
The Gradle build now exposes publication artifacts for release workflows:
- `jar`
- `sourcesJar`
- `javadocJar`
- `generatePomFileForMavenJavaPublication`
- `publishToMavenLocal`

To verify the publication locally without pushing anywhere:

```bash
./gradlew generatePomFileForMavenJavaPublication sourcesJar javadocJar
```

## License
Copyright [2024] [xubo]

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
