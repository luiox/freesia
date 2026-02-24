# freesia

一个高性能、低延迟的事件系统，要求 Java 8 及以上。

## 核心特性

- 运行时零反射分发：注册时通过 `MethodHandle + LambdaMetafactory` 固化调用站点，触发阶段直接走函数接口调用。
- 异步监听器支持：`@Listener(async = true)` 自动提交到内部线程池（默认 `CachedThreadPool`）。
- 优先级排序：`@Listener(priority = int)`，数值越大越先执行，注册时完成静态排序。
- 可取消传播：事件实现 `Cancellable` 后，一旦 `isCancelled()` 为 `true`，后续监听器停止执行。
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

```java
public class PriceTickEvent {
	public final double price;

	public PriceTickEvent(double price) {
		this.price = price;
	}
}
```

如果需要支持中断传播：

```java
import com.github.luiox.freesia.Cancellable;

public class OrderEvent implements Cancellable {
	private volatile boolean cancelled;

	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	public void cancel() {
		this.cancelled = true;
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

bus.addListener(listener);
bus.post(new PriceTickEvent(100.25));
bus.removeListener(listener);
```

## 单元测试

- 运行全部测试：`./gradlew.bat test`
- 覆盖文件：`src/test/java/com/github/luiox/freesia/EventManagerTest.java`
- 覆盖场景：注册/注销、优先级顺序、异步监听、过滤器、可取消传播、静态方法监听

## 性能测试

项目内置了一个轻量 benchmark（默认跳过，避免影响日常 CI）：

- 基准文件：`src/test/java/com/github/luiox/freesia/EventManagerPerformanceTest.java`
- 运行命令：`./gradlew.bat test -Dfreesia.benchmark=true --tests com.github.luiox.freesia.EventManagerPerformanceTest`
- 快捷命令：`./gradlew.bat benchmarkTest`
- 输出指标：`ops/s` 与 `ns/op`

建议在稳定环境下多次执行，关注中位数结果，避免单次抖动带来的误差。
