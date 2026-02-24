# freesia

一个高性能、低延迟的事件系统，要求 Java 8 及以上。

## 通过 JitPack 引入

仓库地址：`https://github.com/luiox/freesia`

依赖坐标格式：`com.github.luiox:freesia:<tag>`

### Gradle (Groovy DSL)

```groovy
repositories {
		mavenCentral()
		maven { url 'https://jitpack.io' }
}

dependencies {
	implementation 'com.github.luiox:freesia:v0.2'
}
```

### Gradle (Kotlin DSL)

```kotlin
repositories {
		mavenCentral()
		maven("https://jitpack.io")
}

dependencies {
	implementation("com.github.luiox:freesia:v0.2")
}
```



## 在 JitPack 发布步骤

> 已完成仓库侧发布配置（`maven-publish`）。

1. 提交并推送当前代码到 GitHub 主分支。
2. 创建版本标签（示例）：

```bash
git tag v1.9
git push origin v1.9
```

3. 打开 `https://jitpack.io/#luiox/freesia`，点击 **Get it** 触发构建。
4. 构建成功后即可通过 `com.github.luiox:freesia:v1.9` 引入。

## 核心特性

- 运行时零反射分发：注册时通过 `MethodHandle + LambdaMetafactory` 固化调用站点，触发阶段直接走函数接口调用。
- 异步监听器支持：`@Listener(async = true)` 自动提交到内部线程池（默认 `CachedThreadPool`）。
- 优先级排序：`@Listener(priority = int)`，数值越大越先执行，注册时完成静态排序。
- 可取消传播：事件实现 `ICancellable` 后，一旦 `isCancelled()` 为 `true`，后续监听器停止执行。
- 并发安全：核心结构基于 `ConcurrentHashMap + CopyOnWriteArrayList`，高频触发路径无锁读取。

## 注解示例

```java
@Listener(priority = 100, async = false)
public void onEvent(MyEvent event) {
	// handle
}
```

## 快速上手

### 1) 定义事件

最简单方式是直接使用内置的 `Event`：

```java
import com.github.luiox.freesia.Event;

Event event = new Event();
```

也可以自定义事件类型：

```java
import com.github.luiox.freesia.Event;

public class PriceTickEvent extends Event {
	public final double price;

	public PriceTickEvent(double price) {
		this.price = price;
	}
}
```

### 2) 声明监听器

```java
import com.github.luiox.freesia.handler.Listener;

public class TradeListener {
	@Listener(priority = 100)
	public void onPrice(PriceTickEvent event) {
		// 高频同步逻辑
	}

	@Listener(async = true)
	public void onOrderAsync(OrderEvent event) {
		// 耗时逻辑（IO、网络请求）
	}
}
```

### 3) 注册、触发、注销

```java
import com.github.luiox.freesia.EventBus;
import com.github.luiox.freesia.EventManager;

EventBus bus = new EventManager();
TradeListener listener = new TradeListener();

bus.register(listener);
bus.post(new PriceTickEvent(100.25));
bus.unregister(listener);
```

### 4) 最简示例

```java
import com.github.luiox.freesia.Event;
import com.github.luiox.freesia.EventBus;
import com.github.luiox.freesia.EventManager;
import com.github.luiox.freesia.handler.Listener;

public class Demo {
	public static class MyListener {
		@Listener
		public void onEvent(Event event) {
			System.out.println("received");
		}
	}

	public static void main(String[] args) {
		EventBus bus = new EventManager();
		MyListener listener = new MyListener();

		bus.register(listener);
		bus.post(new Event());
		bus.unregister(listener);
	}
}
```

