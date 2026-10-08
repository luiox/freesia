# freesia

一个零分配、低延迟的 Java 事件总线。**需要 Java 11+**（构建用 `options.release = 11` 锁住）。

Java 11 是硬下限，不是保守选择：派发路径依赖 `MethodHandles.privateLookupIn`、`Method.trySetAccessible` 和 `System.Logger`（都是 Java 9），诊断用的 `List.copyOf` 是 Java 10。把 release 钉死是为了让编译器在有人误用更高版本 API 时直接报错，而不是把这个下限变成别人构建里的一个莫名编译错误。

## 通过 JitPack 引入

仓库地址：`https://github.com/luiox/freesia`

依赖坐标格式：`com.github.luiox:freesia:<tag>`

```groovy
repositories {
	mavenCentral()
	maven { url 'https://jitpack.io' }
}

dependencies {
	implementation 'com.github.luiox:freesia:v2.2'
}
```

## 2.0 的破坏性变更

从 1.x 升级需要改代码。每一处都对应一个「不报错但行为是错的」缺陷，而不是风格调整。

| 变更 | 原因 |
|---|---|
| 监听器扫描改为遍历类继承链和接口 | 原实现只用 `getDeclaredMethods()`，不返回父类方法。**写在基类里的监听器一个都不会注册，不报错**，功能静默失效 |
| 监听器去重从 `TreeSet` 改为有序表 | `compareTo` 只比较优先级，同优先级的两个监听器比较结果为 0，`TreeSet` 视作重复元素丢弃。**一个模块里两个默认优先级的监听器只有一个生效** |
| 删除 `@Listener(async)` | 默认线程池是 `Executors.newCachedThreadPool()`，无界。每个异步监听器每次派发分配一个捕获 lambda，且顺序完全乱掉。CPU 密集计算应该交给显式的任务调度器，而不是一个注解 |
| `priority` 从 `int` 改为 `ListenerPriority` 枚举 | 数值魔法（`HIGHEST + 1`）让派发顺序无法从源码推断。细粒度排序用新的 `order()` |
| `Event` 不再默认实现 `ICancellable` | 绝大多数事件不可取消，统一实现会让每个派发循环都白付一次检查。改用 `CancellableEvent` |
| 新增 `SingletonEvent` / `SingletonCancellableEvent` | 热路径零分配的写法，配合重入检测 |
| 监听器异常不再 `printStackTrace` 后吞掉 | 改为路由到 `ListenerErrorHandler`，默认实现会指明是哪个监听器、哪个事件 |
| 派发路径不再每次分配迭代器 | `CopyOnWriteArrayList` 的迭代器每次派发都分配；改为 COW 扁平数组快照 |
| `Handler`/`EventHandler`/`EventHandlerScanner` 更名 | 现为 `EventListener`/`ListenerScanner` |

## 核心特性

**零分配派发。** 每个事件类型对应一个 COW 扁平数组，派发只做一次 volatile 读加一次下标循环。单例事件实测 **0 bytes/post**，对照新建事件是 16 bytes/post。

**确定的顺序。** 派发顺序完全由 `priority` → `order()` → 注册序决定，同一优先级下类内按方法名字母序。`getDeclaredMethods()` 的返回顺序 JVM 不作保证，所以不做排序的话每次运行的派发顺序都可能不同——**顺序不可复现等于无法调试**。

**监听器继承。** 基类和接口上的监听器都会被找到，重写的方法只注册一次（最派生的那个），类方法优先于接口默认方法。

**异常隔离但不静默。** 一个监听器抛异常不会中断其余监听器，同时失败会被上报并指明来源。

**线程守卫（可选）。** `EventThreadGuard` 可以声明某个事件只允许特定线程派发。渲染线程和客户端线程共享模块可变字段是真实的数据竞争，守卫把它变成第一次就崩溃，而不是线上偶发。

**重入检测。** 单例事件在派发途中被再次派发会抛 `ReentrantPostException`。契约错误属于总线的问题，不当作监听器失败隔离。

## 计算调度器（2.2 新增）

事件总线管「通知」，计算调度器管「算」。CPU 密集、可以忍受一拍延迟的工作（搜索、预判、打分）不该挤在通知线程上，也不该每个项目自己手写守护线程加 sleep 轮询——这两条路一个拖垮帧时间，一个空转烧 CPU。调度器只有两个类型：

```java
ComputeScheduler scheduler = ComputeScheduler.withDefaultThreads(); // 核数-1，上限 6，守护线程

LatestTask<Plan> task = new LatestTask<>() {
    @Override
    protected Plan compute() {
        return plan(snapshot);   // 纯函数：输入输出全部不可变，不碰任何活动状态
    }
};
scheduler.execute(task);
long lastSubmitted = task.seq;

// 下一拍，提交线程上：
Plan plan = task.takeIfLatest(lastSubmitted);  // 陈旧、未完成、失败 -> 全部是 null
if (plan != null) {
    consume(plan);
}
```

三条契约，违反任何一条都会产生「偶尔行为异常、重开一次又好了」的 bug：

1. **`compute()` 是纯函数**，只读构造时抓好的不可变快照，不碰任何会被别人改写的状态。
2. **消费只走 `takeIfLatest`**。丢弃陈旧结果是强制的，不是可选的——陈旧结果描述的世界已经不存在了。
3. **一拍最多一个在飞的任务**。新的提交覆盖旧的，没有背压，因为背压在这里没有意义。

任务抛异常时 `done` 保持 `false`，`takeIfLatest` 返回 `null`，worker 继续接活；异常同时上报给 `addExceptionHandler` 注册的处理器（默认打一条 warning 日志）。线程是守护线程，`shutdown()` / `close()` 之后拒绝新任务并返回 `false`，调用方据此知道「永远不会有结果了」。

## 实测数据

在 64 核机器上、warmup 20 万次、迭代 200 万次：

| 场景 | 结果 |
|---|---|
| 单例事件，单监听器 | 11.2 ns/op |
| 新建事件，单监听器 | 8.8 ns/op |
| 64 监听器 | 350 ns/op（每个监听器约 5.5 ns） |
| 单例事件分配量 | 0 bytes/post |
| 新建事件分配量 | 16 bytes/post |
| register | 81.6 µs/op |

最后一行不是常数：它被 `LambdaMetafactory` 在运行时生成 lambda 类主导，每个不同的方法签名都要新生成一个类。50 个模块的启动开销约 4ms，可以接受，但要知道它是存在的——**不要在运行期反复注册注销**。

64 监听器那一行也顺便回答了一个常见疑问：多态派发每个监听器约 5.5ns。100 个监听器每秒 400 次事件 = 每秒 0.22ms。**megamorphic 调用不是瓶颈，别在这里花时间。**

跑基准：

```bash
./gradlew benchmarkTest
```

## 快速上手

### 定义事件

```java
public final class PriceTick extends Event {
	public final double price;

	public PriceTick(double price) {
		this.price = price;
	}
}
```

高频事件用单例，热路径就完全不分配：

```java
public final class Tick extends SingletonEvent {
	private static final Tick INSTANCE = new Tick();

	public static Tick get() {
		INSTANCE.reset();
		return INSTANCE;
	}

	public int count;

	@Override
	public void reset() {
		this.count = 0;
	}
}
```

单例事件的契约见 `SingletonEvent` 的 javadoc，简短版：**每次获取时 `reset()`，用完即抛，不得逃逸出派发调用栈，不得同类型重入派发。**

不可取消的事件继承 `Event` / `SingletonEvent`，可取消的继承 `CancellableEvent` / `SingletonCancellableEvent`。取消在每个监听器**返回之后**检查，所以取消者自己会执行，其后的跳过。

### 声明监听器

```java
public final class TradeListener {
	@Listener(priority = ListenerPriority.HIGH)
	public void onPrice(PriceTick event) {
		// 先跑
	}

	@Listener
	public void onPrice2(PriceTick event) {
		// 后跑
	}
}
```

监听器可以写在基类或接口的 default 方法上，子类自动继承；重写只注册一次。

### 注册与派发

```java
EventBus bus = new EventManager();
bus.register(new TradeListener());

PriceTick event = bus.post(new PriceTick(100.25));   // 返回同一实例，字段可读回
boolean busy = bus.hasListeners(PriceTick.class);    // 热路径上先问一句
```

### 线程守卫

```java
bus.setThreadGuard((eventType, poster) -> poster == clientThread);
```

### 异常处理

```java
bus.setErrorHandler((listener, event, error) -> {
	disableOffender(listener.owner());
	incrementFailureCount(listener.describe());
});
```

## 派发语义

**按精确运行时类派发。** `TickEvent` 的监听器收不到 `TickEvent.Pre`。继承链不参与查找：一旦参与，每次新增子类都要重算缓存，还要防止父类监听器被子类重复注册。事件家族靠包结构和命名约定表达，不要靠 Java 类型系统。

**监听器返回值。** 派发是通知式的，返回 `void`。要「传回一个新值」，在事件上加可写字段，hook 侧 `post` 之后读回。

## 许可

MIT
