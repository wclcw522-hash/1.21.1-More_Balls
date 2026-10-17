package com.mcmodworkspace.moreballs.client;

import com.mcmodworkspace.moreballs.BallBehavior;
import com.mcmodworkspace.moreballs.BallCharge;
import com.mcmodworkspace.moreballs.ModEntities;
import com.mcmodworkspace.moreballs.ModItems;
import com.mcmodworkspace.moreballs.ModMenus;
import com.mcmodworkspace.moreballs.MoreBalls;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * 客户端专属：实体渲染器注册、收纳袋界面、球堆数量装饰、蓄力等级 HUD。
 *
 * <p>用 {@link EventBusSubscriber} 的 {@code value = Dist.CLIENT} 做隔离：
 * FML 只在客户端加载并注册这个类，专用服务端上它根本不会被触碰，
 * 也就不会把客户端专属类拖进服务端的类加载器。</p>
 *
 * <p>⚠️ <b>1.21.1 与 26.2 的差别（这一版删掉了两块）</b>：</p>
 * <ul>
 *   <li>{@code RegisterSpecialModelRendererEvent} —— 1.21.1 <b>没有</b>这套 special 模型渲染器，
 *       物品的特殊外观要走 {@code IClientItemExtensions.getCustomRenderer()}（BEWLR）。
 *       组合球与球堆的图标渲染因此另起一套实现，不在这里注册。</li>
 *   <li>{@code RegisterSelectItemModelPropertyEvent} —— 同理，1.21.1 的物品模型没有
 *       「按数据组件值选模型」的能力，弩装填换模型改用 {@code ItemProperties} 的 predicate。</li>
 *   <li>{@code GuiGraphicsExtractor} —— 1.21.1 的 GUI 绘制类叫 {@link GuiGraphics}。</li>
 * </ul>
 */
@EventBusSubscriber(modid = MoreBalls.MOD_ID, value = Dist.CLIENT)
public final class MoreBallsClient {

    /** HUD 文字与底边的距离：物品名画在 hotbar 上方，蓄力条再往上让一格 */
    private static final int HUD_ABOVE_HOTBAR = 72;

    private static final int CHARGE_TEXT_COLOR = 0xFFFFC04D;

    private MoreBallsClient() {
    }

    /**
     * 【球堆】图标角落那个数量 —— 见 {@link BallClusterDecorator}。
     *
     * <p>球堆是 {@code stacksTo(1)} 的，原版不会给它画堆叠数字（那个只读
     * {@code ItemStack#getCount()}），所以得自己挂一个 {@code IItemDecorator}。</p>
     */
    @SubscribeEvent
    public static void onRegisterItemDecorations(RegisterItemDecorationsEvent event) {
        event.register(ModItems.BALL_CLUSTER.get(), BallClusterDecorator.INSTANCE);
    }

    /**
     * 客户端初始化：注册弩的物品模型 predicate（{@link ChargedBall}）。
     *
     * <p>1.21.1 没有 26.2 那套 {@code RegisterSelectItemModelPropertyEvent}，
     * 物品模型的「按条件换贴图」只能走 {@code ItemProperties} 的 predicate，
     * 而它必须在客户端 setup 阶段注册。</p>
     */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(ChargedBall::register);
    }

    /**
     * 资源包重载时的清理钩子。
     *
     * <p>26.2 这里清的是「弩上组合球贴图」的 CPU 合成缓存。1.21.1 上那套外观改走
     * BEWLR（{@code IClientItemExtensions}），等它实现就位后再把清理接进来；
     * 现在保留空的监听器，只为让注册链路先成形。</p>
     */
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new SimplePreparableReloadListener<Void>() {
                    @Override
                    protected Void prepare(ResourceManager manager, ProfilerFiller profiler) {
                        return null;
                    }

                    @Override
                    protected void apply(Void unused, ResourceManager manager, ProfilerFiller profiler) {
                        // TODO(1.21.1 移植)：组合球在弩上的外观改为 BEWLR 后，在这里清它的贴图缓存
                    }
                });
    }

    /** 球的投射物复用原版 ThrownItemRenderer：按实体携带的物品堆渲染，无需写渲染代码 */
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.BALL.get(), BallRenderer::new);
        event.registerEntityRenderer(ModEntities.BALL_ENDER_PEARL.get(), ThrownItemRenderer::new);
        // 滴水石锥（【尖刺】射出去的锥子）—— 外观就是 minecraft:pointed_dripstone 那件物品，
        // 缩小到 0.6 倍，因为作者要的是「**最小的**滴水石锥」。
        event.registerEntityRenderer(ModEntities.DRIPSTONE_SHARD.get(),
                context -> new ThrownItemRenderer<com.mcmodworkspace.moreballs.entity.DripstoneShard>(
                        context, 0.6F, true));
        // 被召唤的蜜蜂 —— 直接复用原版蜜蜂的渲染器（泛型绑的就是 Bee，我们继承自 Bee）。
        event.registerEntityRenderer(ModEntities.SUMMONED_BEE.get(),
                net.minecraft.client.renderer.entity.BeeRenderer::new);
    }

    /** 把收纳袋的菜单绑到界面上 */
    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.BALL_POUCH.get(), BallPouchScreen::new);
    }

    /**
     * 组合球与球堆的图标渲染（BEWLR）。
     *
     * <p>26.2 那边走的是 {@code RegisterSpecialModelRendererEvent} +
     * {@code minecraft:special} 模型；<b>1.21.1 没有这套</b>，
     * 物品的自定义外观只能挂 {@code IClientItemExtensions#getCustomRenderer}
     * —— 也就是 BEWLR。见 {@link BallIconRenderer}。</p>
     */
    @SubscribeEvent
    public static void onRegisterClientExtensions(
            net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        event.registerItem(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            private BallIconRenderer renderer;

            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null) {
                    this.renderer = BallIconRenderer.create();
                }
                return this.renderer;
            }
        }, ModItems.COMBO_BALL.get(), ModItems.BALL_CLUSTER.get());
    }

    /**
     * 蓄力等级显示 —— 位置在物品栏上方、物品名称上方（作者指定）。
     *
     * <p>只在「手里正拿着可蓄力的球并且正在蓄力」时出现。</p>
     */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !player.isUsingItem()) {
            return;
        }

        ItemStack stack = player.getUseItem();

        // ===== 球堆：手上是**壳**，能不能蓄力要看**壳里那颗球**（作者 2026-10-15）=====
        //
        // 壳**不在** `#more_balls:balls` 标签里，`handles` 对它返回 false，
        // 直接问 stack 的话球堆蓄力时那行字永远不显示。
        // `asBall` 把壳摊成里面耐久最低的那颗球（散球原样返回）。
        ItemStack probe = com.mcmodworkspace.moreballs.BallClusterItem.isCluster(stack)
                ? com.mcmodworkspace.moreballs.BallClusterItem.asBall(stack)
                : stack;
        if (!BallBehavior.handles(probe)) {
            return;
        }
        BallBehavior.BallProfile profile = BallBehavior.profileFor(probe);
        int maxLevel = profile.chargeLevels();
        if (maxLevel <= 0) {
            return;
        }

        int level = BallCharge.levelAt(BallCharge.usedTicks(player), maxLevel);
        // 常态不显示：只有真正蓄上力（等级 ≥ 1）才把指示露出来（作者指定）。
        if (level < 1) {
            return;
        }
        Component label = Component.translatable("hud.more_balls.charge", level, maxLevel);

        GuiGraphics graphics = event.getGuiGraphics();
        Font font = minecraft.font;
        int centerX = minecraft.getWindow().getGuiScaledWidth() / 2;
        int y = minecraft.getWindow().getGuiScaledHeight() - HUD_ABOVE_HOTBAR;
        graphics.drawCenteredString(font, label, centerX, y, CHARGE_TEXT_COLOR);
    }
}
