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
