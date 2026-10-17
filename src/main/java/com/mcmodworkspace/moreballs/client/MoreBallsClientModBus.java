package com.mcmodworkspace.moreballs.client;

import com.mcmodworkspace.moreballs.MoreBalls;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/**
 * <b>客户端专属、挂在 MOD 总线上的注册</b> —— 单独一个类，只为了一件事：
 * <b>让专用服务端根本看不到它们</b>。
 *
 * <h2>为什么必须单独一个类（2026-10-15 实测踩到的坑）</h2>
 *
 * <p>原来这些行写在 {@link MoreBalls} 的构造里（{@code modEventBus.addListener(MoreBallsClient::...)}）。
 * 当时的注释断言「方法引用是延迟解析的，专用服务器永远不会加载到 MoreBallsClient」——
 * <b>那句话是错的</b>。方法引用会触发目标类的解析与校验，而 {@code MoreBallsClient}
 * 的成员签名里带着 {@code LocalPlayer} / {@code Font} 这些客户端专属类型 ⇒ 专用服务端启动时
 * {@code NoClassDefFoundError: net/minecraft/client/player/LocalPlayer}，
 * 于是<b>这一线的模组在专用服务端上根本起不来</b>。</p>
 *
 * <p>把注册搬进本类、并标注 {@code value = Dist.CLIENT}，FML 只在客户端加载它。</p>
 *
 * <p>⚠️ <b>1.21.1 与 26.2 的差别</b>：26.2 这里还有一条
 * {@code RegisterSpecialModelRendererEvent}（组合球装填外观的自绘渲染器）。
 * <b>1.21.1 没有 event，也没有 special model renderer 这套机制</b>，
 * 所以这里只剩资源包重载监听。组合球在弩上的外观改用
 * {@code IClientItemExtensions}（BEWLR）另做。</p>
 */
@EventBusSubscriber(modid = MoreBalls.MOD_ID, value = Dist.CLIENT)
public final class MoreBallsClientModBus {

    private MoreBallsClientModBus() {
    }

    /** 资源包重载时清掉「按贴图算出来的组合球缓存」 */
    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        MoreBallsClient.registerReloadListeners(event);
    }
}
