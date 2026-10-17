package com.mcmodworkspace.moreballs;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;

/**
 * <b>切球</b>：一条配方覆盖所有球，产物按输入推导。
 *
 * <h2>为什么要自己写一个</h2>
 * <p>原版 {@link StonecutterRecipe} 的产物是<b>固定</b>的 —— 它没法「按输入决定输出」。
 * 而切球必须这样：木球切出来的半球得记着「我来自木球」，铜球切出来的得记着铜球，
 * 否则合组合球时材质就全错了。</p>
 *
 * <p>于是以前的做法是<b>每颗球手写两个 json</b>（half + quarter），一共 16 个。
 * 每加一颗球都得记得补两份 —— 漏掉就「切不了」。</p>
 *
 * <h2>⚠️ 必须继承 {@link StonecutterRecipe}，不能只实现 {@code SingleItemRecipe}</h2>
 * <p>踩过两次坑，方向正好相反：</p>
 * <ol>
 *   <li><b>直接继承原版</b>时，{@code getSerializer()} 被钉死成
 *       {@code RecipeSerializer<StonecutterRecipe>}，而 {@code RecipeSerializer} 是<b>不变型</b>的
 *       —— 返回自己的 {@code RecipeSerializer<BallCuttingRecipe>} 编译不过。</li>
 *   <li><b>改成 {@code SingleItemRecipe} 之后游戏能跑，但 JEI 一开就崩</b>：
 *       JEI 的石切分类在 {@code StoneCuttingRecipeCategory#isHandled} 里会把配方
 *       <b>强转成 {@code StonecutterRecipe}</b>，于是 {@code ClassCastException}。</li>
 * </ol>
 * <p>结论：<b>继承是硬要求</b>（JEI 与配方书都指望这个类型）。</p>
 *
 * <p>⚠️ <b>1.21.1 与 26.2 的差别</b>：26.2 的 {@code RecipeSerializer} 是 record、
 * 且 {@code getSerializer()} 被钉死成泛型不变的 {@code RecipeSerializer<StonecutterRecipe>}；
 * <b>1.21.1 的 {@code SingleItemRecipe.getSerializer()} 返回的是 {@code RecipeSerializer<?>}</b>
 * —— 所以这里不再需要以前那个
 * {@code @SuppressWarnings("unchecked")} 强转，直接给 {@code RecipeSerializer<BallCuttingRecipe>} 即可。</p>
 *
 * <h2>怎么让石切台认它</h2>
 * <p>{@link net.minecraft.world.inventory.StonecutterMenu} 按<b>配方类型</b>筛选配方，
 * 继承之后 {@code getType()} 已经是 {@code RecipeType.STONECUTTING}，天然满足。</p>
 *
 * <h2>配方怎么做到「只写一份」</h2>
 * <p>json 的 {@code ingredient} 写的是汇总标签 {@code #more_balls:balls/cuttable}
 * —— 它本身不含具体物品，只引用两个子分类
 * （{@code #more_balls:balls/solid} 与 {@code #more_balls:balls/hollow}），
 * 所以球<b>必须进对应子分类</b>才切得动；只进 {@code #more_balls:balls} 是不够的。
 * 产物写 {@code ball_half} 或 {@code ball_quarter}，其中 {@code fragment_source}
 * 的占位值会被 {@link #assemble} 换成真实来源。</p>
 *
 * <p>结论：<b>加新球只要进对应的子分类标签 + 进 {@link BallFragments#sources()}，
 * 切割自动就有</b>，不用再写任何 json。</p>
 */
public class BallCuttingRecipe extends StonecutterRecipe {

    public BallCuttingRecipe(String group, Ingredient input, ItemStack result) {
        super(group, input, result);
    }

    /**
     * 序列化器。
     *
     * <p>1.21.1 的 {@link SingleItemRecipe.Serializer} 构造器是 {@code protected}，
     * 所以这里用一个只有一行构造器的子类把它露出来 ——
     * 它的 codec 就是原版 {@code stonecutting} 那一套（group / ingredient / result 三个字段），
     * 所以 json 的写法和原版石切配方完全一致。</p>
     */
    public static final RecipeSerializer<BallCuttingRecipe> SERIALIZER = new BcSerializer();

    private static final class BcSerializer extends SingleItemRecipe.Serializer<BallCuttingRecipe> {
        private BcSerializer() {
            super(BallCuttingRecipe::new);
        }
    }

    /**
     * 产物 = 模板（半球或四分之一球）+ <b>从输入推出来的来源下标</b>。
     *
     * <p>模板决定切出来的是半球还是四分之一球（json 里写的是哪个就是哪个），
     * 这里只把 {@code fragment_source} 改成正确的值。</p>
     *
     * <p>输入不在 {@link BallFragments#sources()} 里时返回模板原样 ——
     * 理论上不会发生（{@code matches} 已经用标签卡过一道），留着兜底免得出现空产物。</p>
     */
    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        ItemStack product = this.result.copy();
        ItemStack probe = BallClusterItem.asBall(input.item());
        if (probe.is(ModItems.COMBO_BALL.get())) {
            // ===== 组合球（作者 2026-10-16 的规则）=====
            // 产物槽里**只放「与第一份同来源的那几份」**（同来源可以叠成一栈），
            // 来源不同的那几份由取产物那一侧补进背包（见 AbstractContainerMenuMixin）。
            //
            // 例：二合一的组合球切四分之一 —— 上半两个象限同来源、下半两个同来源，
            //     于是产物槽显示「上半那份材料的四分之一球 ×2」，下半那两份塞背包。
            boolean quarter = this.result.getItem() == ModItems.BALL_QUARTER.get();
            int firstSource = comboPieceSource(probe, 0, quarter);
            if (firstSource >= 0) {
                int total = quarter ? 4 : 2;
                int same = 0;
                for (int i = 0; i < total; i++) {
                    if (comboPieceSource(probe, i, quarter) == firstSource) {
                        same++;
                    }
                }
                product.set(ModComponents.FRAGMENT_SOURCE.get(), firstSource);
                product.setCount(Math.max(1, same));
            }
            return product;
        }
        int sourceIndex = sourceIndexFor(input.item());
        if (sourceIndex >= 0) {
            product.set(ModComponents.FRAGMENT_SOURCE.get(), sourceIndex);
        }
        return product;
    }

    /**
     * 组合球切开时，<b>第 {@code piece} 份</b>碎片来自哪个象限。
     *
     * <p>份与象限的对应关系随模板走：</p>
     * <ul>
     *   <li><b>四分之一</b>（4 份）—— 一份对一个象限：份 0/1/2/3 → 象限 0/1/2/3</li>
     *   <li><b>半球</b>（2 份）—— 一份对「一半」：份 0 → 象限 0（上半），
     *       份 1 → 象限 2（下半）。半球本身不分上下（同一个物品），
     *       所以这里只需要「上半那份材料」和「下半那份材料」各出一次</li>
     * </ul>
     *
     * @return 该份对应的来源下标；那个象限是空的时返回 -1
     */
    public static int comboPieceSource(ItemStack combo, int piece, boolean quarter) {
        int quadrant = quarter ? piece : (piece == 0 ? 0 : 2);
        if (quadrant < 0 || quadrant > 3) {
            return -1;
        }
        Integer source = combo.get(ModComponents.COMBO_SLOTS.get(quadrant).get());
        return source == null ? -1 : source;
    }

    /**
     * 「输入 → 碎片来源下标」的真正判据，三层往下降：
     *
     * <ol>
     *   <li><b>球堆</b> → 摊开看壳里那颗球（{@link BallClusterItem#asBall}）</li>
     *   <li><b>组合球</b> → <b>第一个象限</b>的来源；另外三个象限由
     *       {@code AbstractContainerMenuMixin} 在取产物时补发</li>
     *   <li><b>普通球</b> → 它在 {@link BallFragments#sources()} 里的位置</li>
     * </ol>
     *
     * <p>公开是为了让取产物那一侧用同一套判据 —— 两边算法必须一致，
     * 否则产物槽里那份和补发的那几份会对不上号。</p>
     */
    public static int sourceIndexFor(ItemStack stack) {
        ItemStack probe = BallClusterItem.asBall(stack);
        if (probe.isEmpty()) {
            return -1;
        }
        if (probe.is(ModItems.COMBO_BALL.get())) {
            Integer first = probe.get(ModComponents.COMBO_SLOTS.get(0).get());
            return first == null ? -1 : first;
        }
        return BallFragments.indexOfBall(probe.getItem());
    }

    /**
     * 除了父类的判据，还要把<b>组合球</b>挡在外面（半球那条路）。
     *
     * <p>组合球落在 {@code #more_balls:balls/solid} 里，所以标签层面它是「可切」的。
     * 但它不在 {@link BallFragments#sources()} 里 —— 一旦被切，
     * 上面的 {@code assemble} 拿到的 {@code indexOfBall} 是 {@code -1}，
     * 产出的就是**没有来源组件的裸半球**：既合不出球也不显示词条。</p>
     */
    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        // ⚠️ **球堆也认** —— 判据按「壳里那颗球」走，和 assemble 同一口径。
        ItemStack probe = BallClusterItem.asBall(input.item());
        if (probe.isEmpty() || sourceIndexFor(probe) < 0) {
            return false;
        }
        // 组合球**不能对半切** —— 组合球只能走四分之一那条配方，一次拆成四片。
        if (probe.is(ModItems.COMBO_BALL.get())
                && this.result.getItem() == ModItems.BALL_HALF.get()) {
            return false;
        }
        // 【切割门槛】耐久必须 ≥ 80%（作者 2026-10-13 指定）——
        // 球的耐久记在自定义组件 TOUGHNESS_LEFT 上（不是原版 MAX_DAMAGE），
        // 所以走 BallItem 的口径；具体算法见 BallItem.canBeCut。
        if (!BallItem.canBeCut(probe)) {
            return false;
        }
        return super.matches(input, level);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER;
    }
}
