# Mechanical Craft Encoder (mce_encoder) — NeoForge 1.21.1 独立模组

把齿轮盛宴(CDR)里“动力合成编码器”的功能移植成一个**只依赖 Create** 的独立模组，
不依赖 MBD2 / LDLib2 / KubeJS，也**不依赖新版 JEI**（工程里只把老版 JEI jar 作为可选的
compile-only 依赖，代码完全不引用 JEI）。

## 功能（与原版一致）
- 方块“动力合成编码器”：GUI 有 36 格材料输入、1 格过滤槽（可选，指定产物）、1 格输出。
- 给方块一个**红石信号**（常通即可）→ 自动在所有 `create:mechanical_crafting` 配方中
  挑选“输入材料刚好能做”且“宽度 ≤ 设定宽度”的配方（按宽度最接近优先）。
- 把配方编码成一个 **Create 纸板包裹**：物品本体是每种材料 1 份样本，
  并带有 `PackageItem.addOrderContext(...)` 的合成订单上下文 —— 与原版写
  `Fragment/OrderContext` 等价（Create 6.0.10 提供了官方 API）。
- 包裹自动推到**正下方**的容器/漏斗（也可用管道/漏斗从侧面/上面抽放材料）。
- GUI 左下角 `[ - ] width: N [ + ]` 调整匹配宽度 1–9（默认 5）。
- 原版外观：模型/贴图直接取自 CDR 包。

## 依赖
- NeoForge 21.1.228+（你整合包同版本）
- Create `[机械动力] create-1.21.1-6.0.10.jar`（已放入 `libs/` 供编译）
- 可选（仅编译期、不引用）：老版 JEI `jei-1.21.1-neoforge-19.27.0.340.jar`（已放入 `libs/`）

## 如何编译（需要一台能联网的机器，Java 21）
1. 安装 JDK 21 与 Gradle（≥ 8.10，推荐 8.14）。也可以直接用 IntelliJ 打开本工程。
2. 命令行在本目录执行：
   ```
   gradle build
   ```
3. 产物：`build/libs/mce_encoder-1.0.0.jar`
4. 把 jar 放进整合包 `mods/` 即可（Create 已装则无需其它前置）。

## 使用方法
1. JEI / 创造搜索 `动力合成编码器`（模组自带一条 5×5 机械动力合成配方，原料为 Create 材料）。
2. 放置方块；漏斗/管道送材料到顶部或侧面，过滤槽（GUI 内）可放产物指定配方。
3. 给红石信号（常通、或每次给脉冲），输出槽会连续吐出“编码好的 Create 包裹”，
   并自动推到正下方容器。
4. 把包裹接入你的 Create 打包机/合成线：**打包机背后（其 FACING 的反方向）必须是一组
   排成该配方形状的机械动力合成器（动力合成器）多方块**，合成端给红石脉冲后，打包机按
   包裹里的订单把内容物分进各合成器格，自动完成动力合成——与原版 CDR 用法一致。

## 产物/配方过滤（黄铜漏斗式，v1.1）
- GUI 顶部“过滤槽”现在接受 **Create 过滤器**（黄铜漏斗同款 `FilterItem`，任意列表/属性过滤器，
  白/黑名单、是否尊重 NBT 都写在其物品数据里；用手右键该过滤器即可编辑）。放进去后，机器只
  会编码/执行“产物通过过滤器”的配方。放**普通物品**=只允许该产物；槽空 = 全部放行。
- 两种模式共用同一过滤判断。

## 墙模式（直接喂料大墙，v1.1 新增）
Create 打包机对“比墙小的配方”无法正确铺格（它按整面连接墙的下标 1:1 塞料）。v1.1 新增
**墙模式**：机器绕过打包机，把材料按世界坐标直接插进墙上对应格的合成器，因此**一面 9×9 大墙
可以承载任意 ≤9×9 的配方**（3×4 / 5×6 都行）。
1. GUI 底部控制行把模式切成 **“墙模式”**（按钮显示黄底）。默认仍是包裹模式。
2. 摆法：把编码器贴着合成器墙放（相邻即可，自动识别整面相连墙）。**合成器墙必须带动力源
   （有轴转动）**，但**装料时先别给它动力**。
3. 送材料进机器 + 给红石信号 → 机器会自动把配方铺到墙的一角并扣料；铺满后再给墙供上动力，
   机器随即触发一次合成，成品从墙“控制器”正前方弹出（照旧用 工作盆/漏斗/箱子 接）。
4. 若铺出来的形状上下/左右反了，用 GUI 的 **X镜像 / Y镜像** 按钮校准（每面墙校准一次记住即可）。
5. 一批合成完（该角清空）后，机器会在“墙无动力”的窗口自动铺下一批，如此循环。
> 注意装料与转动互斥：Create 会在第一格入料后自动开拼，所以**必须断电装料、上电触发**；
> 想连续自动化就周期性切动一下动力（或用可关断的轴）。

## 已知限制（Create 6.0.10 实证）
- Create 包裹内容容量固定 9 格：包裹里每种材料按“配方中出现次数”计数量并聚合；
  若配方需要 **超过 9 种不同材料**，无法装入单个包裹，编码器会跳过（不消耗材料）。
- 包裹模式下，若打包机背后不是对应形状的动力合成器群，打包机只会把包裹内容当普通快递卸到它
  背后的容器（或拒绝并退回包裹），不会凭空产出成品。**小配方放进大墙请改用墙模式**。
- GUI：宽度按钮调整的是“匹配宽度上限”；配方按“宽度 ≤ 设定”且材料足够的机械合成配方，
  取宽度最接近者。
- 墙模式的墙面若含空洞（缺合成器），配方落在洞上的格子会被跳过/放弃装料。

## 代码位置速览
```
src/main/java/mce/encoder/
  MCEEncoder.java                 主类 + ItemHandler 能力注册
  registry/ModContent.java        方块/物品/BE/菜单/创造标签注册
  block/EncoderBlock.java         方块（EntityBlock）
  block/EncoderBlockEntity.java   方块实体：红石触发、配方识别、编码包裹、自动输出、存档
  menu/EncoderMenu.java           容器（输入/过滤/输出槽 + 宽度调整按钮协议）
  client/EncoderScreen.java       客户端界面（宽度 +/-）
  client/ClientEvents.java        Screen 绑定
src/main/resources/assets/mce_encoder/...   模型/贴图/语言
src/main/resources/data/mce_encoder/...    5×5 机械合成配方
```

## 与 CDR 原实现的对应
| CDR（1.20.1，MBD2+KubeJS） | 本模组（Java） |
|---|---|
| `.sm` 机器定义 + LDLib GUI | 自注册方块/BE + AbstractContainerMenu |
| KubeJS `handleChanged()` | BE `tryEncode()`（同算法：干跑验证→宽度排序→网格编码） |
| `PackageItem` + NBT `Fragment/OrderContext` | `PackageItem.containing` + `PackageItem.addOrderContext`（官方等价 API） |
| `machine.customData.width` | BE `width` 字段 + 菜单按钮调整 |
| MBD2 输出槽自动 IO | 输出推到正下方 + 各面 ItemHandler 能力 |

## 已知待验证点（第一次编译如报错请把日志发我）
- NeoForge `ItemStack.save(registries)` / `parseOptional` 签名；
- `MenuType` 构造/`RegisterMenuScreensEvent` 包路径；
- Create 6.0.10 各静态方法签名（已用 javap 对过：`singleRecipe`/`containing`/`addOrderContext` 均存在）。
