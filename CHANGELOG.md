# 变更记录（1.21.1 线）

> 这条线由 **26.2 线（`more_balls` 1.1.3.34）** 移植而来。
> 版本号从 `1.0.0.0` 起独立计数，四段式 `x.x.x.x`（功能 / 机制 / 物品 / 修复）。
> 26.2 线的历史见另一个仓库的 `CHANGELOG.md`。

## [1.0.0.2] - 2026-10-17

### 修复：全部工作台配方与切割配方解析失败

**现象**（`logs/latest.log`）：约 30 条
`Parsing error loading recipe more_balls:<id>`，报错正文是

```
Map entry '#': Failed to parse either.
  First: Not a json array: "minecraft:iron_block";
  Second: Not a JSON object: "minecraft:iron_block"
```

**根因**：26.2 的 `Ingredient` 接受**字符串简写**（`"key": {"#": "minecraft:iron_block"}`），
1.21.1 只接受**数组**（`["minecraft:iron_block"]`）或对象。移植时只改了 java，
漏了数据文件这一层。

**修法**：批量把 `key` 的字符串值转成单元素数组（26 个 shaped 配方），
切割配方的 `ingredient` 同样处理（2 个）。转换后 JSON 全部通过语法校验，
重跑客户端后 `Parsing error` 条数归零。

### 修复：`#more_balls:ball_carriers` 标签加载失败

标签里列了 `minecraft:creaking`（1.21.4+ 生物）与 `minecraft:parched`（26.2 才有），
1.21.1 不存在这两个 id，整份标签加载被拒。已删掉，剩 17 个实体。

### 修复：球堆物品在物品栏里完全隐形

`models/item/ball_cluster.json` 的 fallback 指向 `ball_cluster_base`，
而那张贴图 `ball_cluster_blank` 是 **1×1 空白**（日志有
`Texture ... ball_cluster_blank with size 1x1 limits mip level`）。
真正的外观由 `BallIconRenderer`（BEWLR）画，一旦 BEWLR 没生效，
这个物品在物品栏里就是**看不见的**。已把 fallback 改成指向木球模型，兜底至少可见。

### 新增：诊断日志（用于定位「耐久 / 球堆未生效」）

- `[ball] 参数表就绪检查：木球 toughness=8，铜球 damage=10.0，铁球 toughness=99`
  —— 确认 `BallBehavior.init()` 的数值表真的建起来了（客户端也打）。
  实测结果：**表正常**，所以「耐久不生效」不是表空导致的。
- `[ball] 扣耐久（cause）：8 -> 7/8` —— 每次真正扣耐久时打印。
- `[ball] 扣耐久被跳过（cause）：实体 toughness=0` —— 实体上耐久没被初始化时打印，
  这是最容易静默失效的一步。
- 顺带把 `BallBehavior.init()` 包进 `FMLCommonSetupEvent#enqueueWork`（官方要求的写法，
  并行阶段直接访问注册表有风险）。

## [1.0.0.1] - 2026-10-17

### 修复：没装 Curios 的实例一启动就崩

**现象**：`java.lang.NoClassDefFoundError: top/theillusivec4/curios/api/type/capability/ICurioItem`，
堆栈指向 `com.mcmodworkspace.moreballs.MoreBalls.<init>(MoreBalls.java:24)` →
`ModItems.register(...)`，崩在 mod 构造阶段（`crash-...-fml.txt`，Failure message 是
`[更多球] More_Balls (more_balls) has failed to load correctly`）。

**根因**：`ModItems.registerPouch` 里原本写着

```java
props -> BallPouchCurios.isLoaded()
        ? new BallPouchCurioItem(props.stacksTo(1), tier)
        : new BallPouchItem(props.stacksTo(1), tier)
```

看着有 `isLoaded()` 保护、false 分支根本不会构造那个类。<b>但字节码验证不看分支会不会走</b>：
JVM 链接 `ModItems` 时要检验 `new BallPouchCurioItem(...)` 的结果能否兼容三元表达式的公共类型
`Item`，于是必须把 `BallPouchCurioItem` 加载进来；那个类 `implements ICurioItem` ⇒
立刻要求 Curios 的类 ⇒ 没装 Curios 就崩。

**为什么一直没暴露**：开发环境的 `localRuntime` 里一直有 Curios，26.2 线的测试实例也装了
Curios，所以这条路径从没被走过。换到没装 Curios 的干净 1.21.1 实例立刻炸。

**修法**：
- 新增 `BallPouchCurioFactory`（不 import 任何 Curios 类型），把 `new BallPouchCurioItem(...)` 搬进去
- `BallPouchCurios.createItem(props, tier)` 只在 `isLoaded()` 为真时才调它
- `ModItems.registerPouch` 改成 `props -> BallPouchCurios.createItem(props.stacksTo(1), tier)`

**验证**：临时把 Curios 从 `localRuntime` 移除（复现「玩家没装 Curios」的环境）跑 `runClient` ——
`[更多球] More_Balls` 正常注册、无 `NoClassDefFoundError`、mod 相关 ERROR 为 0；
验证后已恢复 `localRuntime`（开发环境仍要加载 Curios 才能调饰品功能）。

> 这条也提醒了以后加第三方软依赖的做法：**光把调用放进 `if (isLoaded())` 分支不够**，
> 真正带第三方接口的 `new` / 类型引用必须再隔一层类，否则类链接阶段就会被解析。

## [1.0.0.0] - 2026-10-17

**1.21.1 版本线建立 —— 从 26.2 完整移植。**

### 工程

- 版本线：Minecraft 1.21.1 / NeoForge 21.1.255 / JDK 21 / Gradle 8.9
- `mod_id` 仍是 `more_balls`，显示名与 jar 名沿用 `[更多球] More_Balls`
- 源码 101 个 java（30204 行）、资源 627 个文件，逐一过一遍

### 移植中确认的 API 代差（都来自实际编译报错，不是推测）

| 26.2 | 1.21.1 |
|---|---|
| `net.minecraft.resources.Identifier` | `ResourceLocation` |
| `EntityTypes`（复数常量类） | `EntityType`（泛型类，常量挂在它身上） |
| `EntitySpawnReason` | `MobSpawnType` |
| `ContainerInput` | `ClickType` |
| `ItemUseAnimation` | `UseAnim` |
| `appendHoverText(..., TooltipDisplay, Consumer, ...)` | `appendHoverText(..., List<Component>, TooltipFlag)` |
| `Item.use` 返回 `InteractionResult` | 返回 **`InteractionResultHolder<ItemStack>`** |
| `Recipe.display()` / `placementInfo()` / `RecipeDisplay` | 不存在 → `getIngredients()` + `getResultItem()` |
| `RecipeSerializer` record 式构造 | 接口 → `SimpleCraftingRecipeSerializer` / `SingleItemRecipe.Serializer` 子类 |
| `SavedDataType` + codec 自动序列化 | `SavedData.Factory` 两件套 + 手写 `save(CompoundTag, Provider)` |
| `AttachmentType.Builder.serialize(MapCodec)` | 只收 `Codec<T>` |
| `ValueInput` / `ValueOutput` | `CompoundTag` |
| `MobEffect.applyEffectTick(ServerLevel, LivingEntity, int)` | `(LivingEntity, int)` |
| `MobEffect.isInstantaneous()` | `isInstantenous()`（拼写） |
| `DustParticleOptions(int, float)` | `DustParticleOptions(Vector3f, float)` |
| `ItemCooldowns.addCooldown(ItemStack, int)` | 收 `Item` |
| `EntityType.create(Level, MobSpawnType)` | `create(Level)` |
| `Entity.spawnAtLocation(ServerLevel, ItemStack, float)` | `spawnAtLocation(ItemStack, float)` |
| `Entity.snapTo(...)` | `moveTo(...)` |
| `Entity.entityTags()` | `getTags()` |
| `Entity.is(TagKey<EntityType<?>>)` | `getType().is(tag)` |
| `Entity.onRemoval(RemovalReason)` | `onRemovedFromLevel()` |
| `Entity.interact(Player, InteractionHand, Vec3)` | 两参数（无 Vec3） |
| `Level.isBrightOutside()` | `isDay()` |
| `Level.recipeAccess()` | `getRecipeManager()` |
| `ChunkPos.x()` / `.z()` | 字段 `x` / `z` |
| `GuiGraphicsExtractor` | `GuiGraphics` |
| `RenderPipelines` + `blit(...)` | 无该参数的重载 |
| `SubmitNodeCollector` / `SpecialModelRenderer` / `CameraRenderState` | 不存在 → `EntityRenderer.render(...)` 传统签名 + BEWLR |
| `SelectItemModelProperty` | `ItemProperties` predicate |
| `KeyMapping.Category` 对象 | 分类字符串 + 四参构造 |
| `ClientPacketDistributor` | `PacketDistributor` |
| `net.neoforged.neoforge.client.gui.GuiLayer` | `net.minecraft.client.gui.LayeredDraw.Layer` |
| `AddClientReloadListenersEvent` | `RegisterClientReloadListenersEvent` |

### 两个隐蔽的编译陷阱（记下来免得重蹈）

1. **`ModTags` 里有个同名嵌套类 `EntityType`**，遮蔽了 `net.minecraft.world.entity.EntityType`，
   于是 `TagKey<EntityType<?>>` 一直报「类型 EntityType 不带有参数」。已改名 `Entities`。
2. **1.21.1 的 `Bee` 把 `addAdditionalSaveData` 声明成 `public`**，
   子类覆盖时写 `protected` 会因「可见性收窄」编译失败，而报错文本看起来像参数类型不对。

### 启动阶段踩到的坑

- `neoforge.mods.toml` 的 `versionRange` 原先生成 `[21.1.0,1.22)` ——
  Maven 版本比较里 **1.22 < 21.1.0**，范围倒挂 ⇒ fml 直接判「不是合法 mod 文件」，
  模组根本加载不了。已改成只写下界 `[21.1.0,)`（NeoForge 官方 MDK 同样写法）。
  **loader 与 MC 是两套版本号，不能拿 MC 的 next minor 当 loader 上界。**

### 语义降级（1.21.1 上没有对应物）

- 雪球-铜粒的掉落物：`Items.COPPER_NUGGET`（1.21.2 才有）→ **粗铜**，数量与概率不变
- 嘎枝（`Creaking`，1.21.4+）的持球 AI 分支整体移除
- `Entity.getAirDrag()` / `isAffectedByBlocks()` 两个钩子 1.21.1 没有，相应覆盖移除
- `ServerPlayer.forceSetRotation` 没有纯朝向包 → 降级为 `setYRot/setXRot/setYHeadRot`

### 资源层

- 删除 `assets/more_balls/items/`（34 个 26.2 物品模型定义，1.21.1 不认）与 `assets/minecraft/items/`
- `combo_ball` / `ball_half` / `ball_quarter` / `ball_cluster` 补 1.21.1 fallback 模型
- 弩装填换贴图改写为 `assets/minecraft/models/item/crossbow.json`：
  原版 5 条 override 保留 + 组合球 1 条 + 19 颗球各一条 predicate，共 25 条

### 已验证

- `gradle build` **BUILD SUCCESSFUL**
- `gradle runClient` 启动到主菜单：`[更多球] More_Balls 1.0.0.0 (more_balls)` 注册完成，
  资源包 `mod/more_balls` 加载完成，**mod 相关 ERROR 为 0**
- 与 JEI 19.57.0.451、AppleSkin 3.0.9、Curios 9.2.2 同环境启动无冲突

### 后续补齐（同轮完成）

- **组合球与球堆的物品图标：已改用 1.21.1 的 BEWLR**（`BallIconRenderer`）。
  组合球按 `combo_slot_1..4` 分别画四张 `<球id>_quarter_<q>.png` 叠加（透明像素自然合成整球）；
  球堆摊开画壳里那颗球。26.2 的 `SpecialModelRenderer + SubmitNodeCollector#submitCustomGeometry`
  在 1.21.1 不存在，这里改成从 `MultiBufferSource` 直接拿 `VertexConsumer` 手写双面顶点
  （几何空间 0..16、提交前 `scale(1/16)`、背面顶点倒序但 UV 与正面一致）。
  贴图 namespace **固定 `more_balls`、只取物品 path** —— 史莱姆球与末影珍珠是原版物品，
  用它们的 namespace 会拼出不存在的路径（26.2 线上踩过「右下角史莱姆球不渲染」）。
- **JEI 插件：已移植**（`compat/MoreBallsJeiPlugin`）。做两件事：
  ① 给半球/四分之一球/组合球注册子类型解释器（按 `fragment_source` / `combo_sources`），
  否则 JEI 会把它们全并成一颗木球；
  ② 给两条组合球配方补 `ICraftingCategoryExtension` —— JEI 的工作台分类只显示
  「注册过配方扩展」的配方类，而 `CustomRecipe` 子类不自带扩展（原版 `ShapedRecipe` 才有）。
  1.21.1 的 JEI 19.x 没有 26.2 那套 `SlotDisplay`，外观改由 `setRecipe(holder, builder, helper, focuses)`
  直接往布局里摆格子。

### 未移植的一个类（不是漏项）

- `BallTidyButton` —— 在 26.2 源码里它**没有任何外部引用**（孤立类，功能已废弃），
  1.21.1 这版不移植它是对的。

### 仍待实测（需进世界）

- 球飞行手感、热量系统、收纳袋与弹药轮盘、成就触发
- JEI 里的配方页与子类型条目的实际观感
- 组合球 / 球堆的 BEWLR 图标在 GUI 与掉落物里的显示
