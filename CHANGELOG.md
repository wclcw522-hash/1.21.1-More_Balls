# 变更记录（1.21.1 线）

> 这条线由 **26.2 线（`more_balls` 1.1.3.34）** 移植而来。
> 版本号从 `1.0.0.0` 起独立计数，四段式 `x.x.x.x`（功能 / 机制 / 物品 / 修复）。
> 26.2 线的历史见另一个仓库的 `CHANGELOG.md`。

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

### 待补

- 组合球在物品栏的分片合成渲染、球堆图标 → 改 BEWLR
- JEI 插件（配方展示）
- 进世界后的功能实测（球飞行、热量、收纳袋、轮盘、成就）
