# Leader-XD

Leader-XD是一个 Myau 二改的 Minecraft 1.8.9 客户端模组，使用 Java 8 + Kotlin 混合开发

核心特色为 **Material You UI**

> 本项目仅供学习交流。使用作弊类模块可能违反服务器规则并导致封禁，请自行承担风险。

## 特性

### Material You
- 完整移植 Google material-color-utilities 管线，通过 `SchemeTonalSpot` 生成 M3 配色方案
- 种子色运行时提取自 **Windows 桌面壁纸**（QuantizerCelebi + Score 算法），无壁纸时回退 M3 基线紫 `0xFF6750A4`
- 原版 GUI 全面重绘：
  - `GuiScreen.drawBackground` / `GuiSlot` 列表背景 → `surfaceContainer` 纯色
  - `GuiButton` / `GuiOptionButton` / `GuiOptionSlider` → M3 填充按钮（悬浮/按压状态层动画）
  - `GuiTextField` → M3 填充输入框

### 架构
- **事件总线**：`@EventTarget` 注解驱动，覆盖 Tick / Render / Packet / MoveInput / Update 等事件
- **属性系统**：模块设置以 `Property` 泛型类声明（`BooleanProperty`、`FloatProperty`、`TextProperty`、`RangeProperty` 等）

## 命令

游戏内以 `.` 为前缀发送：

| 命令                        | 别名                  | 说明                |
|---------------------------|---------------------|-------------------|
| `.bind <Module> <Bind>`   | `.b`                | 绑定模块按键            |
| `.toggle <Module>`        | `.t`                | 开关模块              |
| `.hide <Module>`          | `.h`                | 在 ArrayList 中隐藏模块 |
| `.show <Module>`          | `.s`                | 取消隐藏              |
| `.config <Name>`          | `.cfg`、`.c`         | 配置保存 / 加载 / 列表    |
| `.list`                   | `.l`、`.modules`     | 列出全部模块            |
| `.enemy <Player>`         | `.e`、`.target`      | 设置攻击目标            |
| `.playerlist`             | `.players`          | 查看玩家列表            |
| `.ign <Name>`             | `.name`、`.username` | 设置自定义 ID          |
| `.denick`                 | —                   | 解析伪装 ID 的真实名字     |
| `.itemname <Item>`        | `.item`             | 查询物品名             |
| `.help`                   | `.commands`         | 命令帮助              |

## 构建

### 环境要求
- JDK 17+（运行 Gradle；产物字节码为 Java 8）
- Gradle 通过仓库内置 wrapper 启动

### 步骤

```bash
# 构建 remap 后的最终 jar
gradlew remapJar

# 产物位于
# build/libs/Leader-Meow.jar
```

| 任务          | 说明              |
|-------------|-----------------|
| `remapJar`  | 输出最终混淆映射的模组 jar |
| `shadowJar` | 仅打包依赖           |
| `runClient` | 开发环境启动客户端       |                                          

### 关键构建配置
- 映射：MCP stable 22-1.8.9
- Forge：1.8.9-11.15.1.2318
- Mixin：运行时 0.7.11-SNAPSHOT，注解处理器 0.8.5-SNAPSHOT

## 项目结构

```
src/main/java/unfair/
├── Unfair.kt              # 客户端入口
├── command/               # 命令系统（CommandManager + commands/）
├── config/                # 配置
├── event/                 # 事件总线（EventTarget / EventType / Priority）
├── events/                # 具体事件类
├── font/                  # 自定义字体渲染
├── init/                  # FMLLoadingPlugin
├── management/            # 各类管理器（Rotation / Blink / Delay / Target ...）
├── mixin/                 
├── module/                # 模块基类 + modules/{combat,misc,movement,player,render}
├── property/              # 属性系统
└── ui/
    ├── MaterialYou.kt     # M3 动态配色方案
    ├── material/          # material-color-utilities 移植
    ├── LeaderButton.kt    # M3 填充按钮
    └── gui/               # 客户端自有界面
```