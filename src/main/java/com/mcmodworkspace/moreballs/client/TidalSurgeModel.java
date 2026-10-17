package com.mcmodworkspace.moreballs.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.mcmodworkspace.moreballs.entity.BallProjectile;

/**
 * 【潮涌】的环绕纹理模型 —— 球在水里时套在球外面的那圈旋转水纹。
 *
 * <p><b>几何完全照原版 {@code SpinAttackEffectModel}（玩家用激流三叉戟时身边那圈）</b>：
 * 两个 16×32×16 的盒子，缩放 0.75 / 1.5，各自每刻绕 Y 轴反向转 {@code -(45 + 5n)} 度 ——
 * 外层慢、内层快，叠起来就是原版那圈水纹。层定义直接用原版注册好的
 * {@link net.minecraft.client.model.geom.ModelLayers#PLAYER_SPIN_ATTACK}，
 * 纹理也用原版的 {@code trident_riptide.png}，<b>一个新资源都不用建</b>。</p>
 *
 * <p>为什么不直接 new 原版那个模型：它的泛型绑死在 {@code Player}（激流的 Avatar）上，
 * 而这里要画的是球的实体。所以只把「壳」换成球的，几何与转速一个字不改。</p>
 *
 * <p>⚠️ <b>1.21.1 与 26.2 的差别</b>：26.2 的 {@code EntityModel} 泛型参数是渲染状态
 * （{@code BallRenderState}），1.21.1 是<b>实体本身</b>；{@code setupAnim} 也从「只吃状态」
 * 变回原版那套六参数签名。所以这里泛型绑 {@link BallProjectile}，
 * 年龄取实体的 {@code tickCount}。</p>
 */
public class TidalSurgeModel extends EntityModel<BallProjectile> {

    /** 原版激流的环绕纹理（玩家身边那圈水纹用的就是它） */
    public static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/trident/trident_riptide.png");

    private static final int BOX_COUNT = 2;
    private final ModelPart[] boxes = new ModelPart[BOX_COUNT];

    /** 自己存一份根节点 —— 1.21.1 的 {@code Model} 没有可用的 {@code root} 字段（26.2 才有） */
    private final ModelPart rootPart;

    public TidalSurgeModel(ModelPart root) {
        // ⚠️ 1.21.1 的 EntityModel 只有两个构造器：() 和 (Function<ResourceLocation, RenderType>)。
        //    它**不接受 ModelPart**（26.2 那边才是 super(root)），所以根节点自己存着、渲染时直接用。
        super(RenderType::entityCutoutNoCull);
        this.rootPart = root;
        for (int i = 0; i < BOX_COUNT; i++) {
            this.boxes[i] = root.getChild("box" + i);
        }
    }

    /**
     * ⚠️ 1.21.1 的 {@code EntityModel} <b>只抽象了 setupAnim</b>，
     * {@code renderToBuffer} 得自己实现（把调用转给根节点）；26.2 那边是 Model 里给好的。
     */
    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer,
                               int packedLight, int packedOverlay, int color) {
        this.rootPart.render(poseStack, buffer, packedLight, packedOverlay, color);
    }

    @Override
    public void setupAnim(BallProjectile entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        for (int i = 0; i < this.boxes.length; i++) {
            float angle = ageInTicks * -(45 + (i + 1) * 5);
            this.boxes[i].yRot = Mth.wrapDegrees(angle) * (float) (Math.PI / 180.0);
        }
    }
}
