# ObjectPool

适用于对象频繁创建和销毁的对象复用池；当复用池中存在实例则复用该实例，如果不存在则创建新的实例

## 使用

### 创建全局对象复用池

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

### Kotlin 简洁写法

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

### 使用`ObjectPoolStoreOwner`创建对象复用池

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

### 为同一类型创建多个独立对象池

当同一个类需要不同复用语义时，传入自定义 `key` 即可隔离对象池。

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

### 获取对象

从对象复用池获取对象,如果复用池存在实例，复用该实例；如果不存在则创建新的实例

```kotlin
val person = pool.obtain("Andy", 16);
```

### 回收对象

对象复用池回收对象，方便复用

```kotlin
pool.recycle(person)
```

不要把同一个仍在使用的实例重复回收到池中。重复 `recycle` 会被忽略，避免同一对象被分发给多个调用方。

### 查看对象池统计信息

你可以通过 `stats()` 获取对象池运行时快照：

```kotlin
val stats = pool.stats()
println(stats.hitCount)
println(stats.missCount)
println(stats.dropCount)
```

推荐这样解读：
- `hitCount` 高，说明对象池确实被命中
- `missCount` 长期高，说明这个池可能没有带来价值
- `dropCount` 高，说明 `maxPoolSize` 可能过小，或者调用方存在重复 `recycle`
- `peakSize` 接近 `maxPoolSize`，说明池容量可能已经打满
- `currentSize` 用于观察当前池中实际保留了多少对象

### 查看整个 Store 的诊断视图

如果你管理多个对象池，可以直接从 `ObjectPoolStore` 导出聚合诊断：

```kotlin
val diagnostics = store.diagnostics()
println(diagnostics.toDebugString())
```

单个池的诊断项会包含：
- `logicalKey`
- `typeName`
- `maxPoolSize`
- `stats`
- `hitRate`
- `dropRate`

### 实现`Reusable`接口

复用对象需实现`Reusable`接口

```kotlin
class Person(var name: String, var age: Int) : Reusable
```

## Benchmark

执行下面的任务可以快速本地对比“直接分配”和“对象池复用”的基线性能：

```bash
./gradlew benchmarkObjectPool
```

这个 benchmark 不依赖额外库，适合快速本地验证；如果要做严格微基准，仍建议后续引入 JMH。

执行下面的任务可以运行正式 JMH 微基准：

```bash
./gradlew jmh
```

当前 JMH 用例覆盖两类对象：
- `smallObjectDirect` / `smallObjectPooled`
- `bufferObjectDirect` / `bufferObjectPooled`
- `sharedBufferDirect` / `sharedBufferPooled`

它们分别用于验证：
- 轻对象和带大缓冲区对象在对象池下的收益差异
- 4 线程共享对象池时，`maxPoolSize` 与 `resetSpan` 对吞吐的影响

仓库默认的 JMH 配置是开发友好的短轮次配置：
- `warmupIterations = 1`
- `iterations = 2`
- `warmup = 1s`
- `timeOnIteration = 2s`

如果你要做对外发布或写正式性能报告，建议适当提高这些参数。

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

当前 Gradle 构建已经具备发布所需的基础产物：
- `jar`
- `sourcesJar`
- `javadocJar`
- `generatePomFileForMavenJavaPublication`
- `publishToMavenLocal`

如果你只想先验证发布产物而不推送到任何远端，可以执行：

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
