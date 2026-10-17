package com.mcmodworkspace.moreballs.client;

import com.mcmodworkspace.moreballs.BallAmmoSelection;
import com.mcmodworkspace.moreballs.MoreBalls;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.minecraft.client.gui.LayeredDraw;

/**
 * 收纳袋的 HUD —— 副手栏上方那一个「弹药格」。
 *
 * <h2>作者指定的样子</h2>
 * <p><b>在副手栏位置的正上方，复制一个和副手栏同款的单格</b>，
 * 格子里放两样东西：<b>当前选中弹药的图标</b> + <b>收纳袋里这种球的总剩余数</b>。</p>
 *
 * <pre>
 *      ┌────┐
 *      │ 🟡3│   ← 弹药格（本 HUD，与原版副手栏同款）
 *      └────┘
 *      ┌────┐
 *      │    │   ← 原版副手栏（原版自己画，我们不插手）
 *      └────┘
 * </pre>
 *
 * <h2>怎么接进 HUD</h2>
 * <p>用 {@link RegisterGuiLayersEvent#registerAboveAll} 把自己的层挂到所有原版层之上 ——
 * 不必知道原版快捷栏那一层叫什么 id，也不用 Mixin 改 {@code Hud}。</p>
 *
 * <p>⚠️ <b>1.21.1 与 26.2 的差别（三处）</b>：</p>
 * <ul>
 *   <li>绘制类 {@code GuiGraphicsExtractor} → {@link GuiGraphics}；
 *       {@code item(...)} → {@code renderItem(...)}，{@code text(...)} → {@code drawString(...)}</li>
 *   <li>{@code blitSprite} 不再需要 {@code RenderPipelines} 参数（1.21.1 只有精灵 + 坐标尺寸那一版）</li>
 *   <li>F1 隐藏 HUD 的开关在 {@code Minecraft.options.hideGui}，不是 26.2 的 {@code gui.hud.isHidden()}</li>
 * </ul>
 */
@EventBusSubscriber(modid = MoreBalls.MOD_ID, value = Dist.CLIENT)
public final class BallPouchHud implements LayeredDraw.Layer {

    /** 借原版副手栏的精灵来画「同一款」的单格（右侧那个，槽口朝右） */
    private static final ResourceLocation SLOT_SPRITE =
            ResourceLocation.withDefaultNamespace("hud/hotbar_offhand_right");

    /** 原版快捷栏尺寸 */
    private static final int HOTBAR_WIDTH = 182;
    private static final int HOTBAR_HEIGHT = 22;

    /** 副手槽离快捷栏的间距（与原版一致） */
    private static final int OFFHAND_GAP = 29;

    /** 单格精灵的尺寸 */
    private static final int SLOT_WIDTH = 29;
    private static final int SLOT_HEIGHT = 24;

    /** 弹药格与副手栏之间的间距 —— 分开一点，两块不粘在一起 */
    private static final int AMMO_SLOT_GAP = 3;

    /** 我方层的 id */
    private static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(MoreBalls.MOD_ID, "pouch_hud");

    @SubscribeEvent
    public static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER_ID, new BallPouchHud());
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        // HUD 被隐藏时（F1）不画
        if (player == null || minecraft.options.hideGui) {
            return;
        }

        // 只用弩时才显示 —— 这个格子本来就是给「用弩选弹」服务的
        if (!(player.getMainHandItem().getItem() instanceof CrossbowItem)) {
            return;
        }

        // 没在轮盘里挑过弹就没什么可显示的，格子也不留
        ItemStack ammo = BallAmmoSelection.selected(player);
        if (ammo.isEmpty()) {
            return;
        }

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();

        // 原版副手槽的位置：快捷栏左边、贴着底边
        int offhandX = screenWidth / 2 - HOTBAR_WIDTH / 2 - OFFHAND_GAP;
        int offhandTop = screenHeight - HOTBAR_HEIGHT;

        // 弹药格落在副手栏正上方（屏幕上位置的上方，互不重叠）
        int slotTop = offhandTop - SLOT_HEIGHT - AMMO_SLOT_GAP;
        renderAmmoSlot(graphics, minecraft, player, ammo, offhandX, slotTop);
    }

    /** 画那一个弹药格：同款单格 + 弹药图标 + 总数 */
    private void renderAmmoSlot(GuiGraphics graphics, Minecraft minecraft,
                                LocalPlayer player, ItemStack ammo, int x, int y) {
        // 一格外观 —— 与副手栏同款精灵
        graphics.blitSprite(SLOT_SPRITE, x, y, SLOT_WIDTH, SLOT_HEIGHT);

        // 图标：16×16 在 29×24 的格子里居中偏上
        graphics.renderItem(ammo, x + 7, y + 4);

        // 数量：压在图标右下角，跟原版物品格的画法一样。
        //
        // ⚠️ **优先读服务端同步过来的那个值**，而不是自己数客户端的袋子内容 ——
        //    袋子的内容存在 ItemStack 的组件里，服务端改了组件客户端不会自动知道
        //    （尤其是挂在饰品栏上的袋子：Curios 只认「栈被换过」）。
        //    自己数就会一直显示旧值，要玩家手动动一下袋子才刷新。
        //
        //    SELECTED_AMMO_COUNT 是带 .sync(...) 的 attachment ⇒ 由框架保证同步。
        //    值 -1 表示还没收到过同步（比如刚进游戏），那就退回本地数一次兜底。
        int synced = player.getData(
                com.mcmodworkspace.moreballs.ModAttachments.SELECTED_AMMO_COUNT.get());
        int count = synced >= 0 ? synced : BallAmmoSelection.countOf(player, ammo);
        String amount = String.valueOf(count);
        graphics.drawString(minecraft.font, amount,
                x + 24 - minecraft.font.width(amount), y + 13, 0xFFFFFFFF, true);
    }
}
