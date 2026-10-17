package com.mcmodworkspace.moreballs.client;

import com.mcmodworkspace.moreballs.BallClusterContents;
import com.mcmodworkspace.moreballs.BallClusterItem;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.IItemDecorator;

/**
 * <b>球堆图标角落那个数量。</b>
 *
 * <h2>为什么需要自己画</h2>
 * <p>原版只给「真的可堆叠」的物品画堆叠数字 —— 它读的是 {@code ItemStack#getCount()}。
 * 而球堆是 {@code stacksTo(1)} 的（壳内部虽然装着一批球，但那是数据组件，不是物品堆的数量），
 * 于是原版<b>不会</b>给它画任何数字，那一格看起来就是个孤零零的壳。</p>
 *
 * <h2>数字从哪来</h2>
 * <p>{@link BallClusterContents#size()} —— 也就是球列的长度，<b>现算的</b>。
 * 所以不存在「取出一颗之后忘了把数字改小」这种失同步的可能：数字本身就是内容。</p>
 *
 * <h2>为什么坐标要照抄原版</h2>
 * <p>原版 {@code GuiGraphics#renderItemDecorations} 里画堆叠数的落点是
 * {@code x + 19 - 2 - font.width(amount), y + 6 + 3}，这里逐字照搬 ——
 * 右对齐、白色（{@code -1}）、带阴影。于是球堆角落的数字和旁边真·可堆叠物品的数字
 * 落在完全相同的像素上，玩家看不出区别。</p>
 *
 * <p>⚠️ <b>1.21.1 与 26.2 的差别</b>：GUI 绘制类从 {@code GuiGraphicsExtractor}
 * 换回 {@link GuiGraphics}，画文字的方法从 {@code text(...)} 换回
 * {@code drawString(...)}。</p>
 */
public final class BallClusterDecorator implements IItemDecorator {

    /** 无状态，全局一个就够 */
    public static final BallClusterDecorator INSTANCE = new BallClusterDecorator();

    private BallClusterDecorator() {
    }

    @Override
    public boolean render(GuiGraphics guiGraphics, Font font, ItemStack stack, int x, int y) {
        BallClusterContents contents = BallClusterItem.contents(stack);
        if (contents.isEmpty()) {
            return false;
        }

        String amount = String.valueOf(contents.size());
        guiGraphics.drawString(font, amount, x + 19 - 2 - font.width(amount), y + 6 + 3, -1, true);
        return false;   // 没改渲染状态，不需要为后面的装饰器重置
    }
}
