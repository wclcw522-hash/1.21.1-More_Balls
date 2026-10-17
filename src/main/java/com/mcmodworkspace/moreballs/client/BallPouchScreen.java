package com.mcmodworkspace.moreballs.client;

import com.mcmodworkspace.moreballs.BallPouchMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * 魔丸收纳袋的界面。
 *
 * <h2>结构：两个箱子界面叠起来</h2>
 * <p>作者指定的做法 —— 界面等于「上面一个带标题的容器、下面再来一个带标题的容器」，底下接玩家物品栏：</p>
 *
 * <pre>
 * ┌──────────────────────┐
 * │  备用区               │ ← 第一个标题区（17 像素）
 * ├──────────────────────┤
 * │ [弹药格 若干行]        │
 * ├──────────────────────┤
 * │  中转区               │ ← 第二个标题区，它同时就是两段之间的分隔
 * ├──────────────────────┤
 * │ [转运格 若干行]        │
 * ├──────────────────────┤
 * │  玩家物品栏            │
 * └──────────────────────┘
 * </pre>
 *
 * <p>背景全部来自原版大箱子那张 {@code textures/gui/container/generic_54.png}：
 * 标题区取纹理最顶上那 17 像素（实心）、槽位区取 v=17 起、玩家背包取 v=126 起。</p>
 *
 * <p>⚠️ <b>1.21.1 与 26.2 的差别（三处）</b>：</p>
 * <ul>
 *   <li>绘制类 {@code GuiGraphicsExtractor} → {@link GuiGraphics}；
 *       {@code extractBackground(...)} → {@code renderBg(...)}</li>
 *   <li>1.21.1 的 {@code AbstractContainerScreen} <b>没有带尺寸的构造器</b>，
 *       界面高度要在构造里赋 {@code this.imageHeight}</li>
 *   <li>1.21.1 的 {@code GuiGraphics.blit} 没有 {@code RenderPipelines} 参数，
 *       贴图走同名重载；画文字用 {@code drawString}</li>
 * </ul>
 */
public class BallPouchScreen extends AbstractContainerScreen<BallPouchMenu> {

    /** 原版大箱子的容器背景 */
    private static final ResourceLocation CONTAINER_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    /** 纹理里标题区的高度 */
    private static final int TITLE_HEIGHT = 17;

    /** 纹理里槽位区的起始 v */
    private static final int SLOTS_V = 17;

    /** 纹理里玩家背包区的起始 v 与高度 */
    private static final int PLAYER_V = 126;
    private static final int PLAYER_H = 96;

    /** 盖住未解锁格子用的银灰色（与容器边框同色系） */
    private static final int LOCKED_COLOR = 0xFFC6C6C6;

    /** 标题文字颜色 */
    private static final int TITLE_COLOR = 0xFF404040;

    /** 标题文字距左 / 距顶的位置（与 AbstractContainerScreen 一致） */
    private static final int TITLE_INSET_X = 8;
    private static final int TITLE_INSET_Y = 6;

    public BallPouchScreen(BallPouchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        // 1.21.1 的构造器不带尺寸，高度按段数算出来再赋给 imageHeight
        this.imageHeight = menu.guiHeight();
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        // ① 第一个标题区 —— 上面显示的名字（「备用区」）由 AbstractContainerScreen 渲染
        blitTitleBar(graphics, x, y);
        y += TITLE_HEIGHT;

        // ② 弹药段
        int ammoH = this.menu.ammoRows() * 18;
        blitSlotRows(graphics, x, y, ammoH);
        y += ammoH;

        // ③ 第二个标题区 —— 写「中转区」。它同时就是两段的分隔，不必另外画线
        blitTitleBar(graphics, x, y);
        graphics.drawString(this.font,
                Component.translatable("container.more_balls.ball_pouch.transit"),
                x + TITLE_INSET_X, y + TITLE_INSET_Y, TITLE_COLOR, false);
        y += BallPouchMenu.SECTION_TITLE_HEIGHT;

        // ④ 转运段
        int transitH = this.menu.transitRows() * 18;
        blitSlotRows(graphics, x, y, transitH);
        y += transitH;

        // ⑤ 玩家背包区（纹理里位置固定）
        graphics.blit(CONTAINER_TEXTURE, x, y, 0, PLAYER_V, this.imageWidth, PLAYER_H);

        // ⑥ 把未解锁的格子盖成银灰色（索引靠后的那些，也就是从右往左）
        //
        // 盖的是「槽位框」那一整块：原版槽位内容区 16×16、外面还有 1 像素边框，
        // 所以从 slot.x - 1 起盖 18×18 正好铺满，相邻槽位之间不留缝。
        for (Slot slot : this.menu.slots) {
            if (slot.isActive() || slot.x < 0) {
                continue;   // 激活的不盖；x < 0 的是玩家背包那种不显示的
            }
            int sx = x + slot.x - 1;
            int sy = this.topPos + slot.y - 1;
            graphics.fill(sx, sy, sx + 18, sy + 18, LOCKED_COLOR);
        }
    }

    /** 贴一条标题栏（纹理最顶上那 17 像素，实心区） */
    private void blitTitleBar(GuiGraphics graphics, int x, int y) {
        graphics.blit(CONTAINER_TEXTURE, x, y, 0, 0, this.imageWidth, TITLE_HEIGHT);
    }

    /** 从纹理的槽位区取 {@code height} 像素高的一段贴到 (x, y) */
    private void blitSlotRows(GuiGraphics graphics, int x, int y, int height) {
        if (height <= 0) {
            return;
        }
        graphics.blit(CONTAINER_TEXTURE, x, y, 0, SLOTS_V, this.imageWidth, height);
    }
}
