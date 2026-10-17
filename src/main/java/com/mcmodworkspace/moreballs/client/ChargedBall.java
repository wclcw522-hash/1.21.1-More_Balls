package com.mcmodworkspace.moreballs.client;

import com.mcmodworkspace.moreballs.BallAmmo;
import com.mcmodworkspace.moreballs.BallFragments;
import com.mcmodworkspace.moreballs.ModItems;
import com.mcmodworkspace.moreballs.MoreBalls;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 物品模型属性 {@code more_balls:charged_ball} —— 弩里装的是哪一颗球。
 *
 * <h2>为什么要自己写一个属性</h2>
 * 原版弩的模型用 {@code minecraft:charge_type} 分三种情况：装箭 / 装烟花火箭 / 空。
 * 那个属性的取值是 {@code CrossbowItem.ChargeType} 枚举（只有 {@code NONE / ARROW / ROCKET}），
 * <b>封闭的，加不了新值</b>，所以装上球的弩在原版眼里就是「空」，显示的也是空弩模型。
 *
 * <p>于是这里另起一个属性，返回值是<b>这颗球在 {@link BallFragments#sources()} 里的下标 + 1</b>
 * （{@code 0} = 没装球），组合球单独给一个常量值。模型文件用 {@code overrides} 的
 * {@code predicate} 按这个值挑贴图 —— 每种球一条静态分支，
 * 不是球就整个落回原版那套逻辑，箭和烟花火箭的显示分毫不受影响。</p>
 *
 * <p>⚠️ <b>1.21.1 与 26.2 的差别</b>：26.2 用的是物品模型定义系统里的
 * {@code SelectItemModelProperty<String>}（按数据组件值选模型，1.21.4+ 才有）；
 * <b>1.21.1 只有老式的 {@code ItemProperties} predicate</b> ——
 * 它只返回一个 float，所以这里把「哪颗球」编码成序号，字符串 id 那套用不了。</p>
 */
public final class ChargedBall {

    /** 属性 id：{@code more_balls:charged_ball} */
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(MoreBalls.MOD_ID, "charged_ball");

    /** 没装球（或装的不是球） */
    public static final float NONE = 0.0F;

    /**
     * 装着组合球时的取值。
     *
     * <p>组合球不在 {@link BallFragments#sources()} 里（它靠数据组件区分材质），
     * 给它一个足够大、不会和任何球下标撞车的常量。</p>
     */
    public static final float COMBO = 100.0F;

    private ChargedBall() {
    }

    /**
     * 注册到弩上。要在客户端初始化阶段调用（见 {@link MoreBallsClient}）。
     */
    public static void register() {
        ItemProperties.register(Items.CROSSBOW, ID, (ClampedItemPropertyFunction) ChargedBall::value);
    }

    private static float value(ItemStack stack, ClientLevel level, LivingEntity entity, int seed) {
        Item ball = BallAmmo.chargedBallItem(stack);
        if (ball == null) {
            return NONE;
        }
        if (ball == ModItems.COMBO_BALL.get()) {
            return COMBO;
        }
        int index = BallFragments.indexOfBall(ball);
        // -1：是球但不在 sources() 里（比如别家模组往标签里塞的物品）—— 按「没装球」处理
        return index < 0 ? NONE : index + 1;
    }
}
