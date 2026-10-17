package com.mcmodworkspace.moreballs;

import net.minecraft.world.item.Item;

/**
 * <b>Curios 收纳袋的隔离工厂</b> —— 存在的唯一理由：把
 * {@code new BallPouchCurioItem(...)} 这句代码挪出「一定会被验证」的类。
 *
 * <h2>为什么必须单独一个类（2026-10-17 崩溃实测）</h2>
 *
 * <p>原先 {@code ModItems.registerPouch} 里是这么写的：</p>
 *
 * <pre>{@code
 * return ITEMS.registerItem(tier.id(), props -> BallPouchCurios.isLoaded()
 *         ? new BallPouchCurioItem(props.stacksTo(1), tier)   // ← 元凶
 *         : new BallPouchItem(props.stacksTo(1), tier));
 * }</pre>
 *
 * <p>看着没问题：{@code isLoaded()} 为 false 时那条分支根本不执行。
 * <b>但字节码验证不看分支会不会走</b> —— JVM 在链接 {@code ModItems} 时会做类型检查，
 * 而检验 {@code new BallPouchCurioItem(...)} 的结果能否兼容 {@code Item}（三元表达式的公共类型），
 * 就必须把 {@code BallPouchCurioItem} 加载进来；那个类 {@code implements ICurioItem}，
 * 于是立刻要求 {@code top.theillusivec4.curios.api.type.capability.ICurioItem}。</p>
 *
 * <p>结果是：<b>没装 Curios 的实例一启动就崩</b>，报</p>
 *
 * <pre>
 * java.lang.NoClassDefFoundError: top/theillusivec4/curios/api/type/capability/ICurioItem
 *   at com.mcmodworkspace.moreballs.MoreBalls.&lt;init&gt;(MoreBalls.java:24)   ← ModItems.register(...)
 * </pre>
 *
 * <p>开发环境一直是「装了 Curios」跑的（{@code localRuntime} 里有），所以这条从没暴露；
 * 26.2 那条线的测试实例也装了 Curios，同样没暴露。换到干净的 1.21.1 实例立刻炸。</p>
 *
 * <h2>修法</h2>
 *
 * <p>把 {@code new BallPouchCurioItem} 搬进本类。{@link BallPouchCurios#createItem} 只在
 * {@code isLoaded()} 为真时才调用这里 —— 而本类被验证/加载本身不涉及任何 Curios 类型
 * （只 import {@code Item.Properties} 与自己的两个类），于是「没装 Curios」那条路彻底干净。</p>
 *
 * <p>⚠️ <b>不要为了省事把这段再挪回 {@code ModItems} 或 {@code BallPouchCurios}</b> ——
 * 一挪回去就恢复成「验证即加载」，崩溃立刻回来。</p>
 */
final class BallPouchCurioFactory {

    private BallPouchCurioFactory() {
    }

    /**
     * 建一个带饰品能力的收纳袋。
     *
     * <p><b>只有确认 Curios 已加载时才允许调用本方法</b>（由 {@link BallPouchCurios#createItem} 把关）。</p>
     */
    static BallPouchItem create(Item.Properties props, BallPouchTier tier) {
        return new BallPouchCurioItem(props, tier);
    }
}
