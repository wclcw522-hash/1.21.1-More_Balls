package com.mcmodworkspace.moreballs;

import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * 组合球的两条合成配方（作者 2026-10-07 指定）。
 *
 * <h2>为什么要自定义配方</h2>
 * <p>原版 {@code crafting_shaped} 的产物是写死在 json 里的，
 * <b>没法把「哪颗球放在哪一格」带进产物的数据组件</b>。
 * 而作者要求的正是「按摆放位置决定贴图」—— 上铁下金 与 上金下铁 出来的贴图不一样，
 * 所以必须由代码读格位、写组件。这就是 {@link CustomRecipe} 的用武之地。</p>
 *
 * <h2>两条配方</h2>
 * <ul>
 *   <li>{@link Vertical} —— <b>2 个半球上下摆</b>（1×2）。
 *       上面那格取它的 {@code half_top}、下面那格取 {@code half_bottom}</li>
 *   <li>{@link Square} —— <b>4 个四分之一球摆成 2×2</b>。
 *       依次对应 {@code quarter_1 / _2 / _3 / _4}</li>
 * </ul>
 *
 * <h2>「顺序随意」的含义</h2>
 * <p>任意来源的半球都能放任意格，配方<b>不限定哪种球放哪</b>；
 * 但「哪一格」决定它在产物里占哪一段贴图。
 * 所以同一个「铁+金」组合，铁放上面得到「铁-金球」、放下面的得到「金-铁球」——
 * <b>是同一件物品</b>（不会重复注册），名字与贴图跟着位置走。</p>
 *
 * <h2>1.21.1 移植说明（与 26.2 的差别）</h2>
 * <p>26.2 那一版靠 {@code display()} + {@code RecipeDisplay}/{@code SlotDisplay} 喂 JEI 与配方书；
 * <b>1.21.1 没有这套展示系统</b> —— {@code Recipe} 接口只有 {@code getIngredients()} 与
 * {@code getResultItem()}。所以这里改成：</p>
 * <ul>
 *   <li>材料格由 {@link #getIngredients()} 给出（「任意半球 / 任意四分之一球」，走物品而不是标签）</li>
 *   <li>产物格由 {@link #getResultItem} 给出 —— <b>带一组真实来源组件的示例球</b>，
 *       不带组件的话 JEI 里显示不出拼出来的样子</li>
 *   <li>{@code isSpecial()} 仍然返回 {@code false}：原版 {@code CustomRecipe} 默认 {@code true}，
 *       而「特殊配方」JEI 与配方书都不显示</li>
 * </ul>
 */
public final class BallComboRecipe {

    /**
     * JEI 与配方书里那两个「成品」格子（二合一 / 四合一共用）。
     *
     * <p>带上一组真实的来源组件 —— 组合球的贴图是按组件选象限拼出来的，
     * 不带组件的裸模板虽然 fallback 会显示木球，但示例摆一组真来源更直观。</p>
     *
     * <p>来源固定成「木 + 铁」（下标 0、2）：两个示例用的是同一组，
     * 这样它们在 JEI 里看起来是同一件成品、只是摆法不同 —— 事实也确实如此。</p>
     */
    public static ItemStack comboResult() {
        // ⚠️ 光写 combo_sources 不够 —— 组合球的**贴图**是按 combo_slot_1..4
        //    选象限拼出来的（见 1.21.1 的 BallComboBallRenderer），
        //    四个 slot 缺失时每层都落到 fallback（木球那一象限），
        //    JEI 里的成品格就显示成一颗纯木球。
        //    真实产物走 BallFragments.makeComboBall，它两个都写，这里必须对齐。
        //
        //    二合一的正确槽值是 [0, 0, 2, 2]：上半两块来自 0、下半两块来自 2
        //    （与 BallFragments.slotValue 的规则一致）。
        List<Integer> indexes = List.of(0, 2);
        ItemStack stack = new ItemStack(ModItems.COMBO_BALL.get());
        stack.set(ModComponents.COMBO_SOURCES.get(), "0,2");
        for (int q = 0; q < ModComponents.COMBO_SLOTS.size(); q++) {
            int value = BallFragments.slotValue(indexes, q);
            if (value >= 0) {
                stack.set(ModComponents.COMBO_SLOTS.get(q).get(), value);
            }
        }
        return stack;
    }

    private BallComboRecipe() {
    }

    /** 2 个半球，上下摆（1 宽 × 2 高） */
    public static final class Vertical extends CustomRecipe {

        public static final RecipeSerializer<Vertical> SERIALIZER =
                new SimpleCraftingRecipeSerializer<>(Vertical::new);

        public Vertical(CraftingBookCategory category) {
            super(category);
        }

        @Override
        public boolean matches(CraftingInput input, Level level) {
            // 只认 1×2 的竖排：横排两格不是作者要求的形状
            if (input.width() != 1 || input.height() != 2) {
                MoreBalls.LOGGER.debug("[ball][combo] Vertical.matches 尺寸不符 {}x{}", input.width(), input.height());
                return false;
            }
            boolean ok = isFragment(input.getItem(0)) && isFragment(input.getItem(1));
            MoreBalls.LOGGER.debug("[ball][combo] Vertical.matches 结果={} 格0={} 格1={}", ok,
                    input.getItem(0), input.getItem(1));
            return ok;
        }

        @Override
        public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
            // CraftingInput 的索引是 x + y * width，1 宽时就是从上到下
            return BallFragments.makeComboBall(List.of(
                    BallFragmentItem.sourceIndex(input.getItem(0)),
                    BallFragmentItem.sourceIndex(input.getItem(1))));
        }

        /**
         * 材料格：两格都是「任意半球」。
         *
         * <p>1.21.1 的 JEI 与配方书读这里（26.2 那一版读的是 {@code display()} 的 SlotDisplay）。</p>
         */
        @Override
        public NonNullList<Ingredient> getIngredients() {
            Ingredient half = Ingredient.of(ModItems.BALL_HALF.get());
            NonNullList<Ingredient> list = NonNullList.create();
            list.add(half);
            list.add(half);
            return list;
        }

        /** 产物格：带示例来源组件的组合球（JEI 里看得出拼成了什么样子）。 */
        @Override
        public ItemStack getResultItem(HolderLookup.Provider registries) {
            return comboResult();
        }

        /**
         * <b>必须重写成 false</b>：{@code CustomRecipe} 默认返回 true，
         * 而「特殊配方」JEI 与配方书都不显示 —— 那正是「JEI 里看不到组合球示例」的原因。
         */
        @Override
        public boolean isSpecial() {
            return false;
        }

        /** 1×2 竖排才会在配方书里给出可放置的提示。 */
        @Override
        public boolean canCraftInDimensions(int width, int height) {
            return width == 1 && height == 2;
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return SERIALIZER;
        }

        /**
         * 这一格算不算「半球」。
         *
         * <p>只认 {@code ball_half} <b>且</b>带 {@code fragment_source} —— 切石机切出来的真实碎片。
         * 裸模板本来就不该能合成（合出来不知道是哪颗球的碎片）。</p>
         */
        private static boolean isFragment(ItemStack stack) {
            return stack.getItem() == ModItems.BALL_HALF.get()
                    && BallFragmentItem.sourceIndex(stack) >= 0;
        }
    }

    /** 4 个四分之一球，2×2 摆放 */
    public static final class Square extends CustomRecipe {

        public static final RecipeSerializer<Square> SERIALIZER =
                new SimpleCraftingRecipeSerializer<>(Square::new);

        public Square(CraftingBookCategory category) {
            super(category);
        }

        @Override
        public boolean matches(CraftingInput input, Level level) {
            if (input.width() != 2 || input.height() != 2) {
                MoreBalls.LOGGER.debug("[ball][combo] Square.matches 尺寸不符 {}x{}", input.width(), input.height());
                return false;
            }
            for (int i = 0; i < 4; i++) {
                if (!isFragment(input.getItem(i))) {
                    MoreBalls.LOGGER.debug("[ball][combo] Square.matches 格{}不是碎片: {}", i, input.getItem(i));
                    return false;
                }
            }
            MoreBalls.LOGGER.debug("[ball][combo] Square.matches 通过");
            return true;
        }

        @Override
        public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
            // 索引 x + y*width、宽度 2 → 0=左上 1=右上 2=左下 3=右下，
            // 与贴图的 quarter_1..4 一一对应
            return BallFragments.makeComboBall(List.of(
                    BallFragmentItem.sourceIndex(input.getItem(0)),
                    BallFragmentItem.sourceIndex(input.getItem(1)),
                    BallFragmentItem.sourceIndex(input.getItem(2)),
                    BallFragmentItem.sourceIndex(input.getItem(3))));
        }

        /** 材料格：四格都是「任意四分之一球」（理由同 {@link Vertical#getIngredients()}）。 */
        @Override
        public NonNullList<Ingredient> getIngredients() {
            Ingredient quarter = Ingredient.of(ModItems.BALL_QUARTER.get());
            NonNullList<Ingredient> list = NonNullList.create();
            for (int i = 0; i < 4; i++) {
                list.add(quarter);
            }
            return list;
        }

        @Override
        public ItemStack getResultItem(HolderLookup.Provider registries) {
            return comboResult();
        }

        /** 同 {@link Vertical#isSpecial()} —— 不重写的话 JEI 不显示。 */
        @Override
        public boolean isSpecial() {
            return false;
        }

        /** 2×2 才会在配方书里给出可放置的提示。 */
        @Override
        public boolean canCraftInDimensions(int width, int height) {
            return width == 2 && height == 2;
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return SERIALIZER;
        }

        /** 同 {@code Vertical.isFragment}，四分之一球的版本 */
        private static boolean isFragment(ItemStack stack) {
            return stack.getItem() == ModItems.BALL_QUARTER.get()
                    && BallFragmentItem.sourceIndex(stack) >= 0;
        }
    }
}
