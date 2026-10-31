# Leader-XD

Leader-XD 是一个 Myau 二改的 Minecraft 1.8.9 客户端模组，使用 Java 8 + Kotlin 混合开发

> 本项目仅供学习交流。使用作弊类模块可能违反服务器规则并导致封禁，请自行承担风险。

### 架构
- **事件总线**：`@EventTarget` 注解驱动，覆盖 Tick / Render / Packet / MoveInput / Update 等事件
- **属性系统**：模块设置以 `Property` 泛型类声明（`BooleanProperty`、`FloatProperty`、`TextProperty`、`RangeProperty` 等）

### 环境要求
- JDK 17+（运行 Gradle；产物字节码为 Java 8）
- Gradle 通过仓库内置 wrapper 启动
