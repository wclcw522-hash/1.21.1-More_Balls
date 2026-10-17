package com.mcmodworkspace.moreballs.client;

import com.mcmodworkspace.moreballs.BallClusterItem;
import com.mcmodworkspace.moreballs.BallFragments;
import com.mcmodworkspace.moreballs.ModComponents;
import com.mcmodworkspace.moreballs.MoreBalls;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * <b>组合球与球堆的物品图标</b> —— 1.21.1 的 BEWLR 实现。
 *
 * <h2>为什么必须自己画</h2>
 * <ul>
 *   <li><b>组合球</b>：它的外观是「四个象限各取一张贴图叠起来」，而象限由数据组件
 *       {@code combo_slot_1..4} 决定 —— 静态 json 模型读不到运行期组件
 *       （26.2 靠物品模型定义系统的 {@code composite} + {@code select}，那是 1.21.4+ 才有的）</li>
 *   <li><b>球堆</b>：要显示<b>壳里那颗球</b>的图标，同样是运行期数据</li>
 * </ul>
 * <p>两者都只能写代码画。1.21.1 的入口是 {@code IClientItemExtensions#getCustomRenderer}
 * —— 也就是 BEWLR（BlockEntityWithoutLevelRenderer）。</p>
 *
 * <h2>画法</h2>
 * <p>物品图标就是一个 16×16 的双面贴片。这里<b>不用</b> {@code ItemRenderer#renderStatic}
 * 去「画一个物品」，而是自己写顶点：</p>
 * <ul>
 *   <li>几何空间取 <b>0..16</b>（与原版物品模型一致），提交前统一 {@code scale(1/16)}</li>
 *   <li>正面 +Z、背面 -Z 各四个顶点；背面顶点倒序但 UV 与正面一致
 *       （否则旋转 180° 会看到「镜像」的球）</li>
 *   <li>组合球画 4 次（每个象限一张 {@code <球id>_quarter_<q>.png}），透明像素自然叠加</li>
 * </ul>
 *
 * <p>⚠️ <b>1.21.1 与 26.2 的差别</b>：26.2 那边是
 * {@code SpecialModelRenderer + SubmitNodeCollector#submitCustomGeometry}；
 * 1.21.1 没有这两样，顶点直接从 {@code MultiBufferSource} 拿 {@link VertexConsumer} 写。</p>
 *
 * <p>⚠️ <b>贴图的 namespace 一律用 {@code more_balls}</b>：史莱姆球与末影珍珠是<b>原版物品</b>
 * （{@code minecraft:slime_ball} / {@code minecraft:ender_pearl}），
 * 但它们的碎片贴图存在本模组下。拿物品自己的 namespace 去拼会得到
 * {@code minecraft:textures/item/slime_ball_quarter_4.png} —— <b>那个文件不存在</b>，
 * 表现是球堆/组合球缺一块（26.2 线上踩过，作者报的「右下角史莱姆球不渲染」）。</p>
 */
public class BallIconRenderer extends BlockEntityWithoutLevelRenderer {

    /** 几何空间是 0..16，提交前乘这个 */
    private static final float MODEL_SCALE = 1.0F / 16.0F;

    private static final float MIN = 0.0F;
    private static final float MAX = 16.0F;

    /** 正面/背面所在的两个 z 平面（照原版物品模型的 7.5 / 8.5） */
    private static final float PLANE_Z_FRONT = 8.5F;
    private static final float PLANE_Z_BACK = 7.5F;

    public BallIconRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet modelSet) {
        super(dispatcher, modelSet);
    }

    /** 便捷构造：从 Minecraft 实例取两个依赖 */
    public static BallIconRenderer create() {
        Minecraft minecraft = Minecraft.getInstance();
        return new BallIconRenderer(minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource buffer, int light, int overlay) {
        // 球堆 → 摊开看壳里那颗球（散球原样返回）
        ItemStack shown = BallClusterItem.isCluster(stack)
                ? BallClusterItem.asBall(stack)
                : stack;

        if (shown.is(com.mcmodworkspace.moreballs.ModItems.COMBO_BALL.get())) {
            renderCombo(shown, poseStack, buffer, light, overlay);
            return;
        }

        ResourceLocation texture = textureFor(shown);
        if (texture == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        drawQuad(poseStack, buffer, texture, light, overlay);
        poseStack.popPose();
    }

    /** 组合球：四个象限各画一张分片贴图，叠成完整球 */
    private void renderCombo(ItemStack ball, PoseStack poseStack,
                             MultiBufferSource buffer, int light, int overlay) {
        List<Item> sources = BallFragments.sources();
        poseStack.pushPose();
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        boolean drewAny = false;
        for (int q = 0; q < 4; q++) {
            Integer value = ball.get(ModComponents.COMBO_SLOTS.get(q).get());
            if (value == null || value < 0 || value >= sources.size()) {
                continue;
            }
            ResourceLocation texture = quarterTexture(sources.get(value), q + 1);
            if (texture == null) {
                continue;
            }
            drawQuad(poseStack, buffer, texture, light, overlay);
            drewAny = true;
        }
        poseStack.popPose();

        // 四个槽全空（裸模板）→ 退回普通球的画法（会落到 combo_piece_0 那张木球切片）
        if (!drewAny) {
            ResourceLocation fallback = textureFor(new ItemStack(ModItemsHolder.WOODEN));
            if (fallback != null) {
                poseStack.pushPose();
                poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
                drawQuad(poseStack, buffer, fallback, light, overlay);
                poseStack.popPose();
            }
        }
    }

    /** 普通球的贴图：约定 {@code more_balls:textures/item/<物品路径>.png} */
    private static ResourceLocation textureFor(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!MoreBalls.MOD_ID.equals(id.getNamespace())) {
            return null;
        }
        return existing("textures/item/" + id.getPath() + ".png");
    }

    /** 某颗球第 {@code quadrant}（1..4）象限的碎片贴图 */
    private static ResourceLocation quarterTexture(Item item, int quadrant) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        // ⚠️ namespace 固定 more_balls，只取 path —— 见类注释
        return existing("textures/item/" + id.getPath() + "_quarter_" + quadrant + ".png");
    }

    /** 拼路径并确认文件真的在，否则渲染管线会拿到 missingno 紫黑格 */
    private static ResourceLocation existing(String path) {
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(MoreBalls.MOD_ID, path);
        if (Minecraft.getInstance().getResourceManager().getResource(texture).isEmpty()) {
            return null;
        }
        return texture;
    }

    /** 提交一个双面贴片（顶点顺序与 UV 约定照 26.2 的弩装载渲染器） */
    private static void drawQuad(PoseStack poseStack, MultiBufferSource buffer,
                                 ResourceLocation texture, int light, int overlay) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
        PoseStack.Pose pose = poseStack.last();

        // ---- 正面（+Z）----
        vertex(consumer, pose, MIN, MAX, 0.0F, 0.0F, PLANE_Z_FRONT, 1.0F, light, overlay);
        vertex(consumer, pose, MIN, MIN, 0.0F, 1.0F, PLANE_Z_FRONT, 1.0F, light, overlay);
        vertex(consumer, pose, MAX, MIN, 1.0F, 1.0F, PLANE_Z_FRONT, 1.0F, light, overlay);
        vertex(consumer, pose, MAX, MAX, 1.0F, 0.0F, PLANE_Z_FRONT, 1.0F, light, overlay);

        // ---- 背面（-Z）：顶点倒序，UV 与正面完全一致 ----
        vertex(consumer, pose, MAX, MAX, 1.0F, 0.0F, PLANE_Z_BACK, -1.0F, light, overlay);
        vertex(consumer, pose, MAX, MIN, 1.0F, 1.0F, PLANE_Z_BACK, -1.0F, light, overlay);
        vertex(consumer, pose, MIN, MIN, 0.0F, 1.0F, PLANE_Z_BACK, -1.0F, light, overlay);
        vertex(consumer, pose, MIN, MAX, 0.0F, 0.0F, PLANE_Z_BACK, -1.0F, light, overlay);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose,
                               float x, float y, float u, float v,
                               float z, float normalZ, int light, int overlay) {
        buffer.addVertex(pose, x, y, z)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, 0.0F, 0.0F, normalZ);
    }

    /** 只为拿一个「木球」引用做兜底，避免直接静态引用 ModItems 造成初始化时序问题 */
    private static final class ModItemsHolder {
        private static final Item WOODEN = com.mcmodworkspace.moreballs.ModItems.WOODEN_BALL.get();

        private ModItemsHolder() {
        }
    }
}
