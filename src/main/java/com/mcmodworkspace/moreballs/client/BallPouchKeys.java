package com.mcmodworkspace.moreballs.client;

import com.mcmodworkspace.moreballs.BallPouchHelper;
import com.mcmodworkspace.moreballs.MoreBalls;
import com.mcmodworkspace.moreballs.network.OpenPouchPayload;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 收纳袋的按键。
 *
 * <h2>两个键</h2>
 * <ul>
 *   <li><b>G</b> —— 打开<b>饰品栏里</b>的收纳袋。袋子拿在手里时按 G 不生效
 *       （手持想开界面，用主手右键）</li>
 *   <li><b>R</b> —— <b>长按 3 刻</b>展开径向菜单（挑弹药）</li>
 * </ul>
 *
 * <p>G 键按下后只发一个空包给服务端，由服务端去找袋子并打开界面 —— 容器界面必须服务端开。</p>
 *
 * <p>⚠️ <b>1.21.1 与 26.2 的差别（四处）</b>：</p>
 * <ul>
 *   <li>{@code KeyMapping.Category} 对象不存在 → 分组用字符串键
 *       {@code key.categories.more_balls}</li>
 *   <li>{@code KeyMapping} 的三参构造（描述 / 键码 / 分类对象）不存在 →
 *       用四参 {@code (描述, InputConstants.Type, 键码, 分类字符串)}</li>
 *   <li>{@code Minecraft.gui.screen()} → {@code Minecraft.screen}</li>
 *   <li>{@code setScreenAndShow(...)} → {@code setScreen(...)}；
 *       {@code ClientPacketDistributor} → {@code PacketDistributor}</li>
 * </ul>
 */
@EventBusSubscriber(modid = MoreBalls.MOD_ID, value = Dist.CLIENT)
public final class BallPouchKeys {

    private BallPouchKeys() {
    }

    /** 按键设置里的分组（「更多球」） */
    private static final String CATEGORY = "key.categories." + MoreBalls.MOD_ID;

    /** G 键 —— 打开饰品栏里的收纳袋 */
    public static final KeyMapping OPEN_POUCH = new KeyMapping(
            "key.more_balls.open_pouch",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_G,
            CATEGORY);

    /** R 键 —— 长按展开径向菜单 */
    public static final KeyMapping RADIAL_MENU = new KeyMapping(
            "key.more_balls.radial_menu",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_R,
            CATEGORY);

    /** 长按判定门槛（毫秒）—— 作者 2026-10-16 从 0.2 秒改为 **3 刻** */
    private static final long HOLD_MILLIS = 150L;   // 3 刻 × 50ms = 150ms

    /** 按下 R 的时刻，-1 表示没按 */
    private static long radialPressedAt = -1L;

    /** 这一轮长按是否已经开过菜单了 */
    private static boolean radialOpened = false;

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_POUCH);
        event.register(RADIAL_MENU);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();

        // G 键：只在世界内、没开其它界面时响应
        if (minecraft.player != null) {
            while (OPEN_POUCH.consumeClick()) {
                PacketDistributor.sendToServer(OpenPouchPayload.INSTANCE);
            }
        }

        tickRadialMenu(minecraft);
    }

    /** R 键的长按判定 */
    private static void tickRadialMenu(Minecraft minecraft) {
        if (minecraft.player == null) {
            radialPressedAt = -1L;
            radialOpened = false;
            return;
        }

        // 轮盘已经开着的时候，这里什么都不做。
        //
        // 关键坑：**界面打开时 KeyMapping 的状态不一定还在更新** —— 实测 GUI 一开
        // isDown() 就变成 false，如果拿它当「松手」的判据，轮盘刚弹出来就会被
        // 自己关掉（表现是「闪一下就没了」）。所以松手检测交给 RadialMenuScreen 自己用 GLFW 原始键状态做。
        if (minecraft.screen instanceof RadialMenuScreen) {
            return;
        }

        boolean down = RADIAL_MENU.isDown();

        if (down) {
            if (radialPressedAt < 0L) {
                radialPressedAt = System.currentTimeMillis();   // 刚按下
                radialOpened = false;
            }
            long held = System.currentTimeMillis() - radialPressedAt;
            if (!radialOpened
                    && held >= HOLD_MILLIS
                    && minecraft.screen == null) {     // 已经在别的界面里就不弹
                // 留一条诊断：万一轮盘还是没出来，日志里能看到按下判定走了多远、身上有几个袋子
                MoreBalls.LOGGER.debug("[pouch] 长按 R 达标，弹轮盘（身上袋子数 {}）",
                        BallPouchHelper.allPouchStacks(minecraft.player).size());
                minecraft.setScreen(new RadialMenuScreen());
                radialOpened = true;
            }
        } else {
            radialPressedAt = -1L;
            radialOpened = false;
        }
    }
}
