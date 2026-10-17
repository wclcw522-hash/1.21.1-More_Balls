package com.mcmodworkspace.moreballs.compat;

import com.mcmodworkspace.moreballs.BallComboRecipe;
import com.mcmodworkspace.moreballs.ModComponents;
import com.mcmodworkspace.moreballs.ModItems;
import com.mcmodworkspace.moreballs.MoreBalls;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.ICraftingGridHelper;
import mezz.jei.api.ingredients.subtypes.IIngredientSubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.registration.IVanillaCategoryExtensionRegistration;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * 「更多球」的 JEI 插件（1.21.1 版）。
 *
 * <h2>这个类解决两件事</h2>
 * <ol>
 *   <li><b>让 JEI 把「同一件物品的不同数据」当成不同条目</b> ——
 *       半球 / 四分之一球 / 组合球全靠数据组件区分材质，
 *       没有子类型解释器时 JEI 会把它们全并成一颗木球</li>
 *   <li><b>让 JEI 的工作台分类认我们的自定义配方</b> ——
 *       JEI 筛配方的判据是「这个配方类注册过 category extension 吗」，
 *       原版 {@code ShapedRecipe} 自带扩展，而{@code CustomRecipe} 子类不自带，
 *       于是 JEI 会把它整个丢掉（表现是「有配方但 JEI 里查不到」）</li>
 * </ol>
 *
 * <p>⚠️ <b>1.21.1 与 26.2 的差别（这一版是重写，不是翻译）</b>：</p>
 * <ul>
 *   <li>26.2 那版靠 {@code SlotDisplay} / {@code ShapedCraftingRecipeDisplay} 描述外观，
 *       还重写了 {@code getIngredients(RecipeHolder)} 返回 {@code List<SlotDisplay>}；
 *       <b>1.21.1 的 JEI 19.x 没有这些</b> —— 外观由
 *       {@code setRecipe(holder, builder, gridHelper, focuses)} 直接往布局里摆格子</li>
 *   <li>26.2 还有 {@code IRecipeManagerPlugin}（管「按焦点找配方」）与
 *       {@code registerAdvanced}（管子类型聚焦）；1.21.1 这边先用子类型解释器 +
 *       分类扩展覆盖主要用途，聚焦转发那套等实际需要再补</li>
 * </ul>
 *
 * <h2>优雅降级</h2>
 * <p>本类只依赖 JEI 的 API（{@code compileOnly}），靠 {@code @JeiPlugin} 注解被 JEI 发现 ——
 * <b>没装 JEI 时这个类根本不会被加载</b>，模组照常单独运行。</p>
 */
@JeiPlugin
public class MoreBallsJeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(MoreBalls.MOD_ID, "jei_plugin");
    }

    /** 碎片（半球 / 四分之一球）按「来源球下标」区分子类型 */
    private static final IIngredientSubtypeInterpreter<ItemStack> FRAGMENT = (stack, context) -> {
        Integer source = stack.get(ModComponents.FRAGMENT_SOURCE.get());
        return source == null ? IIngredientSubtypeInterpreter.NONE : "src:" + source;
    };

    /** 组合球按「来源串」区分子类型（`combo_sources` 是 `"0,2"` 这种） */
    private static final IIngredientSubtypeInterpreter<ItemStack> COMBO = (stack, context) -> {
        String sources = stack.get(ModComponents.COMBO_SOURCES.get());
        return sources == null || sources.isEmpty()
                ? IIngredientSubtypeInterpreter.NONE
                : "combo:" + sources;
    };

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(ModItems.BALL_HALF.get(), FRAGMENT);
        registration.registerSubtypeInterpreter(ModItems.BALL_QUARTER.get(), FRAGMENT);
        registration.registerSubtypeInterpreter(ModItems.COMBO_BALL.get(), COMBO);
    }

    @Override
    public void registerVanillaCategoryExtensions(IVanillaCategoryExtensionRegistration registration) {
        registration.getCraftingCategory().addExtension(
                BallComboRecipe.Vertical.class, new ComboExtension<>(1, 2, ModItems.BALL_HALF.get()));
        registration.getCraftingCategory().addExtension(
                BallComboRecipe.Square.class, new ComboExtension<>(2, 2, ModItems.BALL_QUARTER.get()));
    }

    /**
     * 组合球配方的 JEI 外观：按实际形状摆输入格，产物给一个带示例来源组件的组合球。
     *
     * <p>输入格放的是<b>裸</b>半球/四分之一球（不带来源组件）—— 因为配方本身不挑来源，
     * 而 JEI 的槽位只认物品 + 子类型；裸的正好表示「任意来源」。
     * 产物用 {@link BallComboRecipe#comboResult()}：它带着一组真实来源组件，
     * 所以 JEI 里看得出「拼出来是什么样子」。</p>
     */
    private static final class ComboExtension<R extends CraftingRecipe>
            implements ICraftingCategoryExtension<R> {

        private final int width;
        private final int height;
        private final net.minecraft.world.item.Item input;

        private ComboExtension(int width, int height, net.minecraft.world.item.Item input) {
            this.width = width;
            this.height = height;
            this.input = input;
        }

        @Override
        public void setRecipe(RecipeHolder<R> holder, IRecipeLayoutBuilder builder,
                              ICraftingGridHelper gridHelper, IFocusGroup focuses) {
            for (int y = 0; y < this.height; y++) {
                for (int x = 0; x < this.width; x++) {
                    builder.addInputSlot(1 + x * 18, 1 + y * 18)
                            .setStandardSlotBackground()
                            .addItemStack(new ItemStack(this.input));
                }
            }
            builder.addOutputSlot(1 + this.width * 18 + 20, 1 + (this.height - 1) * 18 / 2)
                    .setOutputSlotBackground()
                    .addItemStack(BallComboRecipe.comboResult());
        }

        @Override
        public int getWidth(RecipeHolder<R> holder) {
            return this.width;
        }

        @Override
        public int getHeight(RecipeHolder<R> holder) {
            return this.height;
        }
    }
}
