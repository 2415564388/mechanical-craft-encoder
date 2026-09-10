# Mechanical Craft Encoder (mce_encoder) — NeoForge 1.21.1 独立模组

把齿轮盛宴(CDR)里“动力合成编码器”的功能移植成一个**只依赖 Create** 的独立模组，
不依赖 MBD2 / LDLib2 / KubeJS，也**不依赖新版 JEI**（工程里只把老版 JEI jar 作为可选的
compile-only 依赖，代码完全不引用 JEI）。

## 功能

### 1. 动力合成编码器（方块）

把任意 `create:mechanical_crafting` 配方**编码成一个 Create 纸板包裹**。

- **GUI**：36 格材料输入（4×9）+ 1 格输出。GUI 内没有过滤槽，也没有按钮——过滤器是方块侧面的物理槽（见下）。
- **红石触发**：只有该 tick 存在红石信号时才会编码。信号**接通的一刻**会立即尝试一次
  （邻块变化触发），此后每 10 tick（0.5 秒）重试一次。因此**常通信号最可靠**。
- **选配方**：遍历当前所有机械合成配方，取**第一个**“产物通过侧面过滤器”且“材料足够”的配方。
  注意：**没有宽度概念**，也不按宽度排序，取决于配方的遍历顺序；想精确指定就靠过滤器限产物。
- **材料校验是干跑**：在物品副本上模拟一遍，确认每个非空格子都能喂上料才会真正扣料；
  中途发现喂不满就**完全不动物品**。
- **包裹内容**：每种材料各取 1 个样本，按物品身份聚合成**最多 9 种不同材料**（Create 包裹的
  9 格容量）。配方需要超过 9 种材料时，本次跳过且**不消耗任何材料**。
- **订单上下文**用 Create 官方 API 写入：`PackageOrderWithCrafts.singleRecipe(...)`
  （每格一个 `BigItemStack`，空格为 EMPTY）+ `PackageItem.addOrderContext(...)`
  —— 等价于旧版 CDR 写 `Fragment` / `OrderContext`。包裹另外记录配方尺寸
  （自定义数据 `mce_w` / `mce_h`），供拆包时定位。
- **自动输出**：包裹生成后自动推向**正下方**的容器/漏斗；推不进去就留在输出槽里，
  输出槽被占用时不编码。
- **输入面**：顶面与四个水平侧面都可入料；**底面是输出**。
- **扳手**：潜行 + 扳手右键可像 Create 部件一样瞬间取下；镐子正常挖掘（需正确工具才有掉落）。
  破坏或取下时，输入格、输出格里的物品**和挂着的过滤器**都会掉落出来。

### 2. 侧面过滤器（黄铜漏斗式）

过滤器是**方块侧面靠上位置的一个物理槽**（Create 的 `FilteringBehaviour`），不是 GUI 里的格子。

- **放**：手持物品**右键方块**即可装上去（槽里已有过滤器时不会覆盖）。
  放普通物品 = 只编“产物是它”的配方；放 Create 的**过滤器物品**（列表 / 属性 / 黑白名单）
  = 按其配置过滤，判断完全走 Create 自己的匹配器。
- **取**：**潜行 + 空手右键**方块把过滤器取回。
- **空槽** = 全部放行。
- 过滤器物品会被渲染在方块侧面的窗口里（`EncoderRenderer`），随时能看到装的是什么。

### 3. 拆包时的大墙自动铺格

Create 的打包机对“比合成器墙小的配方”没法正确铺格（它按整面连接墙的下标 1:1 塞料）。
本模组在**打包机拆包**这一步接管这种情况：

当拆到的包裹带有 `mce_w`/`mce_h`，并且满足：

- 打包机**背面**（`facing` 的反方向）接着一个机械动力合成器，且
- 这个合成器所属的**整面相连墙比配方大**（配方能装下，但不是正好占满）

则本模组**把配方按世界坐标铺到墙的左上角**，而不是走 Create 原本的逻辑。于是
**一面 9×9 的大墙可以承载任意 ≤9×9 的配方**（3×4、5×6 都行）。

实现要点：

- 墙的行列坐标由 Create 自己的 `POINTING` 箭头（merge 空间）推导：没有下游的那一级合成器为
  (0,0)，行 0 = 逻辑最上、列 0 = 逻辑最左，与 Create 的折叠约定一致，
  所以**无论玩家怎么摆墙的朝向，都对应得上**。
- 装料期间把整面墙置为 loading，`MechanicalCrafterBlockEntityMixin` 会在此间取消
  `checkCompletedRecipe`，避免“塞进第一格 Create 就自动开拼”；装完后再给控制端合成器挂一个
  延迟触发标记，等这面墙**真正开始转动**（`getSpeed() != 0`）时执行一次
  **force-free** 的 `checkCompletedRecipe(false)`（force-free 才能容忍大墙上配方周围的空合成器）。
- 装料过程有**回滚**：中途失败会把已插入的物品还回源容器。
- 不满足上述条件（配方正好占满整面墙，或装不下）时**完全走 Create 原逻辑**，本模组不介入。

## 使用方法

1. 在创造物品栏（或 JEI）搜索“动力合成编码器”，放置方块。
2. 用漏斗/管道/手动把配方所需的材料送进机器（顶面或四个侧面），或直接开 GUI 放。
3. （可选）手持物品右键方块装上侧面过滤器，限定要编码的产物。
4. 给机器**持续红石信号** → 输出槽吐出“已编码”的 Create 纸板包裹，并自动推到正下方容器。
5. 把包裹送进 Create 打包机：
   - **配方正好等于墙的大小**：打包机背后摆成该配方形状的合成器群，走 Create 原生流程。
   - **墙比配方大**：打包机背后摆一整面相连的合成器墙，包裹拆开时本模组会把配方自动铺到墙的
     左上角。**装料时要断电，装完再给墙供上动力**（Create 会在第一格入料后自动开拼，所以必须
     断电装料、上电触发）。成品照常从合成器墙产出，用工作盆/漏斗/箱子接。

## 已知限制（基于 Create 6.0.10 实测）

- 一个包裹最多装 **9 种不同材料**（Create 包裹容量），超出就跳过且不消耗材料。
- 选配方**不按宽度、也不按优先级**排序，取遍历到的第一个满足“材料够 + 通过过滤”的配方；
  要精确指定请用侧面过滤器。
- 大墙铺格要求墙面**没有空洞**：配方落在缺失合成器上的格子会导致放弃装料（会回滚）。
- 装料与转动互斥：装料必须在墙**无动力**时进行，装完再上动力触发。
- 输出只往**正下方**推，不会主动往侧面或上方输出。
- 旧版本遗留的“超大包裹”（内容格数超过 Create 的 9 格上限）会在读取时被自动清理，
  以防 Create / 护目镜读取时崩溃。

## 依赖

- NeoForge **21.1.228+**（Minecraft 1.21.1）
- Create **≥ 6.0.10**（硬依赖，`neoforge.mods.toml` 中声明 mandatory、AFTER）
- 可选（仅编译期、代码不引用）：老版 JEI `jei-1.21.1-neoforge-19.27.0.340.jar`

`libs/` 里已放好 Create / Ponder / 老版 JEI 三个 jar 供编译，clone 下来即可直接构建。

## 如何编译

需要 JDK 21 与 Gradle（≥ 8.10）：

```bash
gradle build
```

产物：`build/libs/mce_encoder-1.0.0.jar`。把 jar 放进整合包 `mods/` 即可（装了 Create 就不用别的）。

## 代码位置速览

```
src/main/java/mce/encoder/
  MCEEncoder.java                 主类 + ItemHandler 能力注册
  registry/ModContent.java        方块/物品/BE/菜单/创造标签注册
  block/EncoderBlock.java         方块（EntityBlock + IWrenchable，右键装卸过滤器、扳手取下）
  block/EncoderBlockEntity.java   方块实体：红石触发、选配方、编码包裹、向下输出、存档、旧包裹清理
  menu/EncoderMenu.java           容器（36 输入 + 1 输出 + 玩家背包）
  item/EncoderBlockItem.java      物品提示（一句简介 + 按住 SHIFT 展开详情）
  create/CrafterWall.java         合成器墙几何发现 + 按世界坐标铺料（含失败回滚）
  create/MceCrafterExt.java       注入到 Create 合成器的扩展接口（loading / pending craft 标记）
  create/mixin/MechanicalCrafterBlockEntityMixin.java  装料期抑制自动开拼 + 延迟触发一次合成
  create/mixin/PackagerBlockEntityMixin.java           拆包时接管“大墙铺格”
  client/ClientEvents.java        Screen / 方块实体渲染器注册
  client/EncoderScreen.java       容器 GUI（自绘面板 + 原版 slot 贴图）
  client/EncoderRenderer.java     在方块侧面窗口渲染过滤器物品
src/main/resources/assets/mce_encoder/...   模型 / 贴图 / 语言（zh_cn、en_us）
src/main/resources/data/mce_encoder/...     机器自身的机械合成配方
```

## 与 CDR 原实现的对应

| CDR（1.20.1，MBD2+KubeJS） | 本模组（Java） |
|---|---|
| `.sm` 机器定义 + LDLib GUI | 自注册方块/BE + `AbstractContainerMenu` |
| KubeJS `handleChanged()` | `EncoderBlockEntity.tryEncode()`（干跑验证 → 选配方 → 编码包裹） |
| `PackageItem` + NBT `Fragment/OrderContext` | `PackageItem.containing` + `PackageItem.addOrderContext`（官方等价 API） |
| MBD2 输出槽自动 IO | 输出自动推到正下方 + 各面 ItemHandler 能力 |
| （原版没有的能力） | 拆包时把小于墙的配方按世界坐标铺到墙的一角 |

> 模型与贴图取自 CDR 包，遵循其原始授权。

## 许可证

MIT
