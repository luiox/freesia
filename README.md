# freesia

一个高性能、低延迟的事件系统，要求 Java 8 及以上。

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

