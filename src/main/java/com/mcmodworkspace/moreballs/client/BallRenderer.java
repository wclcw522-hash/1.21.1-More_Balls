package com.mcmodworkspace.moreballs.client;

import com.mcmodworkspace.moreballs.BallBehavior;
import com.mcmodworkspace.moreballs.entity.BallProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 球的投射物渲染器 —— 在 {@link ThrownItemRenderer} 基础上支持<b>按球缩放</b>。
 *
 * <p>原版那个渲染器的缩放是构造参数（写死的），而我们的球共用一个实体类型、
 * 却需要不同大小（金球是「一格内切球」，比普通球大得多）。
 * 普通球的倍率是 1.0，走的是和原版完全一样的路径。</p>
 *
 * <p>⚠️ <b>1.21.1 与 26.2 的差别</b>：26.2 把渲染拆成了「状态快照 + 提交」两段
 * （{@code createRenderState} / {@code extractRenderState} / {@code submit(..., SubmitNodeCollector, CameraRenderState)}），
 * 尺寸必须经 {@code BallRenderState} 传下去；<b>1.21.1 的 {@code EntityRenderer} 仍然是
 * 传统的一步到位 {@code render(entity, yaw, partialTick, poseStack, buffer, light)}</b>，
 * 实体就在手上，直接读它的 profile 即可 —— 所以那一整套 RenderState 在 1.21.1 上不需要。</p>
 */
public class BallRenderer extends ThrownItemRenderer<BallProjectile> {

    /** 环绕层用全亮 —— 它在水下，不该被头顶的方块阴影压暗 */
    private static final int LIGHT_FULL = 0x00F000F0;

    /** 【潮涌】的环绕模型 —— 层定义直接用原版注册好的那个，懒 bake 一次 */
    private TidalSurgeModel surgeModel;

    public BallRenderer(EntityRendererProvider.Context context) {
        super(context, 1.0F, true);
    }

    private TidalSurgeModel surgeModel() {
        if (this.surgeModel == null) {
            this.surgeModel = new TidalSurgeModel(
                    net.minecraft.client.Minecraft.getInstance().getEntityModels()
                            .bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER_SPIN_ATTACK));
        }
        return this.surgeModel;
    }

    @Override
    public void render(BallProjectile entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        float scale = BallBehavior.profileFor(entity.getItem()).entityScale();

        if (scale == 1.0F) {
            super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
            this.renderSurge(entity, partialTick, poseStack, buffer);
            return;
        }

        poseStack.pushPose();
        // 放大是围绕实体原点（脚下）做的，所以往上抬一点，免得大球有半个身子陷进地面
        poseStack.translate(0.0F, (scale - 1.0F) * 0.125F, 0.0F);
        poseStack.scale(scale, scale, scale);
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
        // 缩放分支同样要画环绕层（否则只有普通球的潮涌看得见，金球那种就漏了）
        this.renderSurge(entity, partialTick, poseStack, buffer);
        poseStack.popPose();
    }

    /** 【潮涌】的环绕水纹 —— 球在水里时套在外面那一圈（几何与纹理都来自原版激流） */
    private void renderSurge(BallProjectile entity, float partialTick, PoseStack poseStack,
                             MultiBufferSource buffer) {
        if (!entity.isTidalSurge()) {
            return;
        }
        TidalSurgeModel model = this.surgeModel();
        float age = entity.tickCount + partialTick;
        model.setupAnim(entity, 0.0F, 0.0F, age, 0.0F, 0.0F);

        // 【潮涌】环绕的朝向：原版那圈水纹「绕的是玩家的身体轴」，玩家开激流时人是躺着的，
        // 身体轴自然就对准了视线（= 速度）方向 —— 所以原版看着是对的。
        // 球没有这套：ThrownItemRenderer 只按相机转，球的朝向跟速度无关，
        // 于是环绕的轴会被**锁死在竖直**（作者 2026-10-13 第三轮反馈：「方向被锁定了」）。
        //
        // 这里显式把模型转成「对齐速度方向」：先按 yaw 绕竖轴、再按 pitch 把
        // 「原本朝上的那一面」压到速度方向上。
        float yaw = 0.0F;
        float pitch = 0.0F;
        Vec3 velocity = entity.getDeltaMovement();
        if (velocity.lengthSqr() > 1.0E-6D) {
            yaw = (float) (Mth.atan2(velocity.x, velocity.z) * 180.0D / Math.PI);
            pitch = (float) (Mth.atan2(velocity.y,
                    Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z)) * 180.0D / Math.PI);
        }

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F - pitch));
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(TidalSurgeModel.TEXTURE));
        model.renderToBuffer(poseStack, consumer, LIGHT_FULL, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        poseStack.popPose();
    }
}
