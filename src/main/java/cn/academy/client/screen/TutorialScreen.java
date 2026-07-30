package cn.academy.client.screen;

import cn.academy.AcademyCraft;
import cn.academy.ability.AbilityState;
import cn.academy.client.ACKeyMappings;
import cn.academy.client.render.ACGuiTextures;
import cn.academy.client.render.ACLegacyFont;
import cn.academy.registry.ACItems;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.lwjgl.glfw.GLFW;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Native recreation of GuiTutorial, tutorial.xml and tutorial_windows.xml. */
public final class TutorialScreen extends Screen {
    private static final int FRAME_WIDTH = 427;
    private static final int FRAME_HEIGHT = 240;
    private static final String[] PAGES = {
            "welcome", "ores", "phase_generator", "solar_generator", "wind_generator",
            "metal_former", "imag_fusor", "terminal", "ability_developer", "ability_basis",
            "misc", "develop_ability", "wireless_network", "energy_bridge"
    };
    private static final Pattern IMAGE = Pattern.compile("!\\[([^]]*)]\\(([^)]+)\\)");

    private static final ResourceLocation LEFT_WINDOW = gui("window_tutorial_left.png");
    private static final ResourceLocation LOGO_0 = gui("tutorial/logo0.png");
    private static final ResourceLocation LOGO_1 = gui("tutorial/logo1.png");
    private static final ResourceLocation LOGO_2 = gui("tutorial/logo2.png");
    private static final ResourceLocation LOGO_3 = gui("tutorial/logo3.png");
    private static final ResourceLocation SCROLL_TRACK = gui("button/widget_scroll_1.png");
    private static final ResourceLocation SCROLL_HANDLE = gui("button/widget_scroll_2.png");
    private static final ResourceLocation ARROW_LEFT = gui("button/button_left_2.png");
    private static final ResourceLocation ARROW_RIGHT = gui("button/button_right_2.png");
    private static final ResourceLocation TAG_VIEW = gui("icons/icon_view.png");
    private static final ResourceLocation TAG_CRAFT = gui("icons/icon_craft.png");
    private static final ResourceLocation CRAFT_GRID = gui("tutorial/crafting_grid.png");
    private static final ResourceLocation METAL_FORMER = gui("tutorial_metalformer.png");
    private static final ResourceLocation SMELTING = gui("tutorial_smelting.png");
    private static final ResourceLocation IMAG_FUSOR = gui("tutorial_fusor.png");

    private final PageData[] pageData = new PageData[PAGES.length];
    private final List<PreviewGroup> previews = new ArrayList<>();
    private int selectedPage = -1;
    private int previewGroup;
    private int previewView;
    private float scroll;
    private boolean draggingScroll;
    private boolean firstOpen;
    private long openedAt;
    private long selectedAt;

    private sealed interface ContentBlock permits TextBlock, ImageBlock, SpaceBlock {}
    private record TextBlock(List<String> lines, int color, float size, int height) implements ContentBlock {}
    private record ImageBlock(ResourceLocation texture, int sourceWidth, int sourceHeight,
                              int width, int height) implements ContentBlock {}
    private record SpaceBlock(int height) implements ContentBlock {}
    private record PageData(String title, String brief, List<ContentBlock> blocks, int contentHeight) {}

    private enum PreviewTag { VIEW, CRAFT }
    private enum PreviewKind { ITEM, CRAFTING, SMELTING, METAL_FORMER, IMAG_FUSOR }
    private record Preview(PreviewKind kind, ItemStack target, List<Ingredient> ingredients,
                           int recipeWidth, int recipeHeight, ResourceLocation modeIcon, int phaseAmount) {}
    private record PreviewGroup(PreviewTag tag, ItemStack target, List<Preview> views) {}

    public TutorialScreen() {
        super(Component.translatable("ac.app.tutorial.name"));
    }

    private static ResourceLocation gui(String path) {
        return ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "textures/guis/" + path);
    }

    private float frameScale() {
        return Math.min(width / 480f, (height - 4f) / FRAME_HEIGHT);
    }
    private float frameLeft() { return (width - FRAME_WIDTH * frameScale()) * .5f; }
    private float frameTop() { return (height - FRAME_HEIGHT * frameScale()) * .5f; }
    private float virtualX(double mouseX) { return (float) ((mouseX - frameLeft()) / frameScale()); }
    private float virtualY(double mouseY) { return (float) ((mouseY - frameTop()) / frameScale()); }

    @Override
    protected void init() {
        prepareTextureFiltering();
        openedAt = Util.getMillis();
        if (minecraft != null && minecraft.player != null) {
            firstOpen = !minecraft.player.getPersistentData().getBoolean("academy:tutorial_opened");
            minecraft.player.getPersistentData().putBoolean("academy:tutorial_opened", true);
        }
        for (int i = 0; i < PAGES.length; i++) pageData[i] = loadPage(PAGES[i]);
    }

    private void prepareTextureFiltering() {
        for (ResourceLocation texture : new ResourceLocation[]{LEFT_WINDOW, LOGO_0, LOGO_1, LOGO_2, LOGO_3,
                SCROLL_TRACK, SCROLL_HANDLE, ARROW_LEFT, ARROW_RIGHT, TAG_VIEW, TAG_CRAFT, CRAFT_GRID,
                METAL_FORMER, SMELTING, IMAG_FUSOR}) ACGuiTextures.setLinearFilter(texture);
    }

    private PageData loadPage(String page) {
        String locale = minecraft != null && "zh_cn".equals(minecraft.getLanguageManager().getSelected())
                ? "zh_cn" : "en_us";
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID,
                "tutorials/" + locale + "/" + page + ".md");
        StringBuilder raw = new StringBuilder();
        try {
            Resource resource = minecraft.getResourceManager().getResource(location).orElseThrow();
            try (BufferedReader reader = resource.openAsReader()) {
                reader.lines().forEach(line -> raw.append(line).append('\n'));
            }
        } catch (Exception ignored) {
            raw.append("![title]\n").append(page).append("\n![brief]\n![content]\n")
                    .append(Component.translatable("ac.tutorial.load_failed").getString());
        }
        String source = raw.toString();
        int titleMark = source.indexOf("![title]");
        int briefMark = source.indexOf("![brief]");
        int contentMark = source.indexOf("![content]");
        String title;
        String brief;
        String body;
        if (titleMark >= 0 && briefMark > titleMark && contentMark > briefMark) {
            title = replaceMacros(source.substring(titleMark + 8, briefMark).trim());
            brief = replaceMacros(source.substring(briefMark + 8, contentMark).trim());
            body = source.substring(contentMark + 10);
        } else {
            title = page.replace('_', ' ');
            brief = "";
            body = source;
        }
        List<ContentBlock> blocks = new ArrayList<>();
        int height = 0;
        for (String rawLine : body.split("\\R", -1)) {
            String line = replaceMacros(rawLine.trim());
            Matcher image = IMAGE.matcher(line);
            if (image.matches()) {
                ResourceLocation texture = ResourceLocation.tryParse(image.group(2));
                if (texture != null) {
                    ACGuiTextures.setLinearFilter(texture);
                    int[] dimensions = imageDimensions(texture);
                    int drawWidth = Math.min(150, dimensions[0]);
                    int drawHeight = Math.max(20, Math.round(dimensions[1] * drawWidth / (float) dimensions[0]));
                    blocks.add(new ImageBlock(texture, dimensions[0], dimensions[1], drawWidth, drawHeight));
                    height += drawHeight + 6;
                    continue;
                }
            }
            if (line.isBlank()) {
                blocks.add(new SpaceBlock(5));
                height += 5;
                continue;
            }
            int headingLevel = 0;
            while (headingLevel < line.length() && line.charAt(headingLevel) == '#') headingLevel++;
            String clean = line.substring(headingLevel).stripLeading();
            boolean heading = headingLevel > 0;
            if (!heading && clean.matches("^[-*+]\\s+.*")) clean = "• " + clean.substring(2);
            float size = heading ? headingLevel == 1 ? 11 : 9 : 8;
            int step = heading ? headingLevel == 1 ? 14 : 12 : 10;
            String styled = markdownText(clean).getString();
            List<String> lines = ACLegacyFont.wrap(styled, 150, size, false);
            blocks.add(new TextBlock(lines, heading ? 0xFF7DE5FF : 0xFFD9E7EC, size, step));
            height += lines.size() * step + 2;
        }
        return new PageData(title, brief, blocks, height);
    }

    private MutableComponent markdownText(String source) {
        MutableComponent result = Component.empty();
        String value = source.replace("`", "");
        int cursor = 0;
        while (cursor < value.length()) {
            int bold = value.indexOf("**", cursor);
            int underlineBold = value.indexOf("__", cursor);
            int begin = bold < 0 ? underlineBold : underlineBold < 0 ? bold : Math.min(bold, underlineBold);
            if (begin < 0) {
                result.append(Component.literal(stripLink(value.substring(cursor))));
                break;
            }
            if (begin > cursor) result.append(Component.literal(stripLink(value.substring(cursor, begin))));
            String marker = value.substring(begin, begin + 2);
            int end = value.indexOf(marker, begin + 2);
            if (end < 0) {
                result.append(Component.literal(stripLink(value.substring(begin))));
                break;
            }
            result.append(Component.literal(stripLink(value.substring(begin + 2, end)))
                    .setStyle(Style.EMPTY.withBold(true)));
            cursor = end + 2;
        }
        return result;
    }

    private static String stripLink(String value) {
        return value.replaceAll("\\[([^]]+)]\\(([^)]+)\\)", "$1 ($2)");
    }

    private String replaceMacros(String value) {
        int storedId = minecraft == null || minecraft.player == null ? 0 : AbilityState.load(minecraft.player).misakaId();
        int misaka = storedId >= 1000 ? storedId : minecraft == null || minecraft.player == null ? 10032
                : 1000 + Math.floorMod(minecraft.player.getUUID().hashCode(), 18000);
        return value.replace("![misakaname]", Component.translatable("ac.tutorial.misaka", misaka).getString())
                .replace("![key id=\"ability_activation\"]", ACKeyMappings.ACTIVATE.getTranslatedKeyMessage().getString())
                .replace("![key id=\"open_data_terminal\"]", ACKeyMappings.TERMINAL.getTranslatedKeyMessage().getString())
                .replace("![key id=\"switch_preset\"]", ACKeyMappings.SWITCH_PRESET.getTranslatedKeyMessage().getString())
                .replace("![key id=\"edit_preset\"]", ACKeyMappings.EDIT_PRESET.getTranslatedKeyMessage().getString());
    }

    private static int[] imageDimensions(ResourceLocation location) {
        String path = location.getPath();
        if (path.endsWith("ability_ui.png")) return new int[]{200, 256};
        if (path.endsWith("overload.png")) return new int[]{450, 72};
        if (path.endsWith("preset_selection_ui.png")) return new int[]{500, 576};
        if (path.endsWith("skill_tree_ui.png")) return new int[]{486, 250};
        if (path.endsWith("wind_gen.png")) return new int[]{345, 500};
        if (path.endsWith("wind_gen_ui.png")) return new int[]{500, 519};
        return new int[]{128, 128};
    }

    private boolean unlocked(int page) {
        return minecraft == null || minecraft.player == null
                || AbilityState.load(minecraft.player).tutorialUnlocked(PAGES[page]);
    }

    private void selectPage(int page) {
        selectedPage = page;
        selectedAt = Util.getMillis();
        scroll = 0;
        previewGroup = previewView = 0;
        buildPreviews();
    }

    private void buildPreviews() {
        previews.clear();
        if (selectedPage < 0) return;
        switch (PAGES[selectedPage]) {
            case "ores" -> {
                addView(ACItems.CONSTRAINT_METAL.toStack());
                addView(ACItems.IMAGSIL_ORE.toStack());
                addView(ACItems.CRYSTAL_ORE.toStack());
                addView(ACItems.RESO_ORE.toStack());
                addView(ACItems.MATTER_UNIT.toStack());
                addRecipes(ACItems.CONSTRAINT_PLATE.toStack());
                addRecipes(ACItems.IMAG_SILICON_INGOT.toStack());
                addRecipes(ACItems.WAFER.toStack());
                addRecipes(ACItems.IMAG_SILICON_PIECE.toStack());
            }
            case "phase_generator" -> addRecipes(ACItems.PHASE_GEN.toStack());
            case "solar_generator" -> addRecipes(ACItems.SOLAR_GEN.toStack());
            case "wind_generator" -> {
                addRecipes(ACItems.WINDGEN_BASE.toStack()); addRecipes(ACItems.WINDGEN_PILLAR.toStack());
                addRecipes(ACItems.WINDGEN_MAIN.toStack()); addRecipes(ACItems.WINDGEN_FAN.toStack());
            }
            case "metal_former" -> addRecipes(ACItems.METAL_FORMER.toStack());
            case "imag_fusor" -> addRecipes(ACItems.IMAG_FUSOR.toStack());
            case "terminal" -> {
                addRecipes(ACItems.TERMINAL_INSTALLER.toStack()); addRecipes(ACItems.APP_FREQ_TRANSMITTER.toStack());
                addRecipes(ACItems.APP_MEDIA_PLAYER.toStack()); addRecipes(ACItems.APP_SKILL_TREE.toStack());
            }
            case "ability_developer" -> {
                addRecipes(ACItems.DEVELOPER_PORTABLE.toStack()); addRecipes(ACItems.DEV_NORMAL.toStack());
                addRecipes(ACItems.DEV_ADVANCED.toStack());
            }
            case "energy_bridge" -> {
                addRecipes(ACItems.RF_INPUT.toStack()); addRecipes(ACItems.RF_OUTPUT.toStack());
                addRecipes(ACItems.EU_INPUT.toStack()); addRecipes(ACItems.EU_OUTPUT.toStack());
            }
            default -> { }
        }
    }

    private void addView(ItemStack stack) {
        previews.add(new PreviewGroup(PreviewTag.VIEW, stack,
                List.of(new Preview(PreviewKind.ITEM, stack, List.of(), 0, 0, null, 0))));
    }

    private void addRecipes(ItemStack target) {
        List<Preview> views = new ArrayList<>();
        if (minecraft != null && minecraft.level != null) {
            for (var holder : minecraft.level.getRecipeManager().getRecipes()) {
                Recipe<?> recipe = holder.value();
                ItemStack output;
                try { output = recipe.getResultItem(minecraft.level.registryAccess()); }
                catch (RuntimeException ignored) { continue; }
                if (output.isEmpty() || !ItemStack.isSameItem(output, target)) continue;
                if (recipe instanceof AbstractCookingRecipe) {
                    views.add(new Preview(PreviewKind.SMELTING, output.copy(), List.copyOf(recipe.getIngredients()),
                            1, 1, null, 0));
                } else if (recipe instanceof CraftingRecipe) {
                    int recipeWidth = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 0;
                    int recipeHeight = recipe instanceof ShapedRecipe shaped ? shaped.getHeight() :
                            Math.max(1, (recipe.getIngredients().size() + 2) / 3);
                    views.add(new Preview(PreviewKind.CRAFTING, output.copy(), List.copyOf(recipe.getIngredients()),
                            recipeWidth, recipeHeight, null, 0));
                }
            }
        }
        addManualMachineRecipes(target, views);
        if (views.isEmpty()) views.add(new Preview(PreviewKind.ITEM, target, List.of(), 0, 0, null, 0));
        previews.add(new PreviewGroup(PreviewTag.CRAFT, target, views));
    }

    private void addManualMachineRecipes(ItemStack target, List<Preview> views) {
        if (target.is(ACItems.CONSTRAINT_PLATE.get()))
            metal(views, ACItems.CONSTRAINT_INGOT.toStack(), ACItems.CONSTRAINT_PLATE.toStack(), "plate");
        if (target.is(ACItems.IMAG_SILICON_INGOT.get()))
            metal(views, ACItems.IMAGSIL_ORE.toStack(), new ItemStack(ACItems.IMAG_SILICON_INGOT.get(), 4), "refine");
        if (target.is(ACItems.WAFER.get()))
            metal(views, ACItems.IMAG_SILICON_INGOT.toStack(), new ItemStack(ACItems.WAFER.get(), 2), "incise");
        if (target.is(ACItems.IMAG_SILICON_PIECE.get()))
            metal(views, ACItems.WAFER.toStack(), new ItemStack(ACItems.IMAG_SILICON_PIECE.get(), 4), "incise");
        if (target.is(ACItems.CRYSTAL_NORMAL.get()))
            fusor(views, ACItems.CRYSTAL_LOW.toStack(), target, 3000);
        if (target.is(ACItems.CRYSTAL_PURE.get()))
            fusor(views, ACItems.CRYSTAL_NORMAL.toStack(), target, 8000);
    }

    private void metal(List<Preview> views, ItemStack input, ItemStack output, String mode) {
        ResourceLocation icon = gui("icons/icon_former_" + mode + ".png");
        ACGuiTextures.setLinearFilter(icon);
        views.add(new Preview(PreviewKind.METAL_FORMER, output, List.of(Ingredient.of(input)), 1, 1,
                icon, 0));
    }

    private void fusor(List<Preview> views, ItemStack input, ItemStack output, int amount) {
        views.add(new Preview(PreviewKind.IMAG_FUSOR, output, List.of(Ingredient.of(input)), 1, 1, null, amount));
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        renderBackground(gui, mouseX, mouseY, partialTick);
        float scale = frameScale();
        float vx = virtualX(mouseX), vy = virtualY(mouseY);
        gui.pose().pushPose();
        gui.pose().translate(frameLeft(), frameTop(), 0);
        gui.pose().scale(scale, scale, 1);

        float leftPartX = 7, partY = (FRAME_HEIGHT - 220.5f) * .5f;
        float introTime = (Util.getMillis() - openedAt) / 1000f;
        float leftAlpha = firstOpen ? blend(introTime, 1.75f, .3f) : 1;
        gui.setColor(1, 1, 1, leftAlpha);
        gui.blit(LEFT_WINDOW, Math.round(leftPartX), Math.round(partY), 85, 221,
                0, 0, 340, 882, 340, 882);
        gui.setColor(1, 1, 1, 1);

        boolean listVisible = !firstOpen || introTime > 2.4f;
        if (listVisible) drawTutorialList(gui, vx, vy, leftPartX, partY, leftAlpha);

        float rightX = 92, rightY = partY;
        if (selectedPage < 0) drawIntro(gui, rightX, rightY, introTime);
        else {
            float fade = 1 - blend((Util.getMillis() - selectedAt) / 1000f, 0, .3f);
            if (fade > 0) drawIntroLogos(gui, rightX, rightY, introTime, fade);
            drawSelected(gui, vx, vy, rightX, rightY);
        }
        gui.pose().popPose();
        for (var renderable : renderables) renderable.render(gui, mouseX, mouseY, partialTick);
    }

    private void drawTutorialList(GuiGraphics gui, float mouseX, float mouseY, float x, float y, float alpha) {
        float listX = x + 6.6f, listY = y + 7;
        for (int i = 0; i < PAGES.length; i++) {
            float rowY = listY + i * 12;
            boolean hover = inside(mouseX, mouseY, listX, rowY, 72, 12);
            if (hover || selectedPage == i) gui.fill(Math.round(listX), Math.round(rowY),
                    Math.round(listX + 72), Math.round(rowY + 12), selectedPage == i ? 0x663FD5F3 : 0x4DFFFFFF);
            int color = unlocked(i) ? withAlpha(0xFFFFFF, alpha) : withAlpha(0x999999, alpha);
            String title = pageData[i] == null ? PAGES[i] : pageData[i].title;
            drawFittedText(gui, title, listX + 3, rowY + 1.5f, 67, 10, color, Align.LEFT);
        }
    }

    private void drawIntro(GuiGraphics gui, float rightX, float rightY, float time) {
        drawIntroLogos(gui, rightX, rightY, time, 1);
        // Original expanding center line under logo1.
        float lineProgress = firstOpen ? Mth.clamp((time - .4f) / .5f, 0, 1) : 1;
        int center = Math.round(rightX + 166);
        int half = Math.round(100 * lineProgress);
        gui.fill(center - half, Math.round(rightY + 169), center + half, Math.round(rightY + 171),
                withAlpha(0xFFFFFF, lineProgress));
    }

    private void drawIntroLogos(GuiGraphics gui, float x, float y, float time, float multiplier) {
        float a3 = firstOpen ? blend(time, .1f, .3f) : 1;
        float a2 = firstOpen ? blend(time, .65f, .3f) : 1;
        float a1 = firstOpen ? blend(time, 1.3f, .3f) : 1;
        float a0 = firstOpen ? blend(time, 1.75f, .3f) : 1;
        float logo3Y = firstOpen ? Mth.lerp(blend(time, .7f, .4f), 63, -36) : -36;
        drawTexture(gui, LOGO_0, x + 53.6f, y + 9.25f, 225, 137, 899, 548, a0 * multiplier);
        drawTexture(gui, LOGO_1, x + 53.6f, y + 139.75f, 225, 59, 899, 236, a1 * multiplier);
        drawTexture(gui, LOGO_2, x + 53.6f, y + 139.75f, 225, 59, 899, 236, a2 * multiplier);
        drawTexture(gui, LOGO_3, x + 147.4f, y + 91.6f + logo3Y, 37, 37, 149, 149, a3 * multiplier);
    }

    private void drawSelected(GuiGraphics gui, float mouseX, float mouseY, float x, float y) {
        PageData data = pageData[selectedPage];
        boolean learned = unlocked(selectedPage);
        if (learned) drawContent(gui, data, x, y);

        float showX = x + 173.5f;
        float showY = y;
        drawPreview(gui, mouseX, mouseY, showX, showY);

        float descY = y + 138.5f;
        gui.blit(LEFT_WINDOW, Math.round(showX), Math.round(descY), 159, 82,
                0, 0, 340, 882, 340, 882);
        drawFittedText(gui, data.title, showX + 9, descY + 9, 140, 10, 0xFFFFFFFF, Align.LEFT);
        drawWrapped(gui, data.brief, showX + 9, descY + 23, 140, 8, 0xFFD9E7EC, 9, 6);
    }

    private void drawContent(GuiGraphics gui, PageData data, float x, float y) {
        float textX = x + 5, textY = y + 5, textWidth = 150, textHeight = 210.5f;
        float max = maxContentScroll();
        scroll = Mth.clamp(scroll, 0, max);
        int scissorLeft = Math.round(frameLeft() + textX * frameScale());
        int scissorTop = Math.round(frameTop() + textY * frameScale());
        int scissorRight = Math.round(frameLeft() + (textX + 157) * frameScale());
        int scissorBottom = Math.round(frameTop() + (textY + textHeight) * frameScale());
        gui.enableScissor(scissorLeft, scissorTop, scissorRight, scissorBottom);
        float cursorY = textY + 3 - scroll;
        for (ContentBlock block : data.blocks) {
            if (block instanceof SpaceBlock space) cursorY += space.height;
            else if (block instanceof TextBlock text) {
                for (String line : text.lines) {
                    if (cursorY >= textY - text.height && cursorY <= textY + textHeight)
                        drawSequence(gui, line, textX + 3, cursorY, text.size, text.color);
                    cursorY += text.height;
                }
                cursorY += 2;
            } else if (block instanceof ImageBlock image) {
                if (cursorY + image.height >= textY && cursorY <= textY + textHeight)
                    gui.blit(image.texture, Math.round(textX + (textWidth - image.width) * .5f), Math.round(cursorY),
                            image.width, image.height, 0, 0, image.sourceWidth, image.sourceHeight,
                            image.sourceWidth, image.sourceHeight);
                cursorY += image.height + 6;
            }
        }
        gui.disableScissor();

        ACGuiTextures.blit(gui, SCROLL_TRACK, x + 162.5f, y + 2, 10, 217,
                0, 0, 19, 433, 19, 433, .32f, .78f, 1, .32f);
        float progress = max <= 0 ? 0 : scroll / max;
        ACGuiTextures.blit(gui, SCROLL_HANDLE, x + 162.5f, y + 2 + progress * 163, 10, 53,
                0, 0, 19, 106, 19, 106, .72f, .94f, 1, .9f);
    }

    private void drawPreview(GuiGraphics gui, float mouseX, float mouseY, float x, float y) {
        if (previews.isEmpty()) return;
        previewGroup = Mth.clamp(previewGroup, 0, previews.size() - 1);
        PreviewGroup group = previews.get(previewGroup);
        previewView = Mth.clamp(previewView, 0, Math.max(0, group.views.size() - 1));
        Preview preview = group.views.get(previewView);
        float areaX = x + 12.25f, areaY = y - 1, areaSize = 134;
        renderPreview(gui, preview, areaX, areaY, areaSize);

        if (group.views.size() > 1) {
            drawTexture(gui, ARROW_LEFT, x + 5, y + 41.75f, 12, 52, 30, 130,
                    inside(mouseX, mouseY, x + 5, y + 41.75f, 12, 52) ? 1 : .72f);
            drawTexture(gui, ARROW_RIGHT, x + 140, y + 41.75f, 12, 52, 30, 130,
                    inside(mouseX, mouseY, x + 140, y + 41.75f, 12, 52) ? 1 : .72f);
        }
        float tagX = x + 12, tagY = y + 120.75f;
        for (int index = 0; index < previews.size(); index++) {
            float tx = tagX + index * 17;
            PreviewGroup value = previews.get(index);
            boolean hover = inside(mouseX, mouseY, tx, tagY, 18, 18);
            ResourceLocation icon = value.tag == PreviewTag.CRAFT ? TAG_CRAFT : TAG_VIEW;
            drawTexture(gui, icon, tx, tagY, 18, 18, 64, 64,
                    index == previewGroup || hover ? 1 : .7f);
            if (hover && value.tag == PreviewTag.CRAFT)
                drawFittedText(gui, Component.translatable("ac.tutorial.crafting", value.target.getHoverName()).getString(),
                        tagX, tagY - 9, 133, 10, 0xFFFFFFFF, Align.LEFT);
        }
    }

    private void renderPreview(GuiGraphics gui, Preview preview, float x, float y, float size) {
        switch (preview.kind) {
            case ITEM -> renderItemOrBlock(gui, preview.target, x, y, size);
            case CRAFTING -> renderCrafting(gui, preview, x, y, size);
            case SMELTING -> renderSmelting(gui, preview, x, y, size);
            case METAL_FORMER -> renderMetalFormer(gui, preview, x, y, size);
            case IMAG_FUSOR -> renderImagFusor(gui, preview, x, y, size);
        }
    }

    private void renderItemOrBlock(GuiGraphics gui, ItemStack stack, float x, float y, float size) {
        if (stack.getItem() instanceof BlockItem blockItem && minecraft != null) {
            gui.pose().pushPose();
            gui.pose().translate(x + size * .5f, y + size * .57f, 80);
            gui.pose().scale(48, -48, 48);
            gui.pose().mulPose(Axis.XP.rotationDegrees(24));
            gui.pose().mulPose(Axis.YP.rotationDegrees((Util.getMillis() / 35f) % 360));
            gui.pose().translate(-.5, -.5, -.5);
            minecraft.getBlockRenderer().renderSingleBlock(blockItem.getBlock().defaultBlockState(), gui.pose(),
                    gui.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            gui.flush();
            gui.pose().popPose();
        } else {
            drawStack(gui, stack, x + size * .5f - 32, y + size * .5f - 32, 64);
        }
    }

    private void renderCrafting(GuiGraphics gui, Preview preview, float x, float y, float size) {
        float bx = x + 8, by = y + 29;
        gui.blit(CRAFT_GRID, Math.round(bx), Math.round(by), 118, 77,
                0, 0, 196, 128, 196, 128);
        String kind = preview.recipeWidth <= 0 ? "shapeless" : "shaped";
        drawFittedText(gui, Component.translatable("ac.gui.crafttype." + kind).getString(),
                x + 8, y + 8, 118, 10, 0xFFFFFFFF, Align.CENTER);
        for (int index = 0; index < preview.ingredients.size() && index < 9; index++) {
            int col = preview.recipeWidth <= 0 ? index % 3 : index % preview.recipeWidth;
            int row = preview.recipeWidth <= 0 ? index / 3 : index / preview.recipeWidth;
            ItemStack stack = alternating(preview.ingredients.get(index), index);
            drawStack(gui, stack, bx + 3 + col * 26, by + 3 + row * 26, 19);
        }
        drawStack(gui, preview.target, bx + 91, by + 29, 19);
    }

    private void renderSmelting(GuiGraphics gui, Preview preview, float x, float y, float size) {
        float bx = x + 9, by = y + 29;
        gui.blit(SMELTING, Math.round(bx), Math.round(by), 115, 77,
                0, 0, 192, 128, 192, 128);
        if (!preview.ingredients.isEmpty()) drawStack(gui, alternating(preview.ingredients.getFirst(), 0), bx + 18, by + 26, 19);
        drawStack(gui, preview.target, bx + 74, by + 26, 19);
    }

    private void renderMetalFormer(GuiGraphics gui, Preview preview, float x, float y, float size) {
        float bx = x + 19, by = y + 19;
        gui.blit(METAL_FORMER, Math.round(bx), Math.round(by), 96, 96,
                0, 0, 192, 192, 192, 192);
        if (preview.modeIcon != null) gui.blit(preview.modeIcon, Math.round(bx + 41), Math.round(by + 11), 13, 13,
                0, 0, 64, 64, 64, 64);
        if (!preview.ingredients.isEmpty()) drawStack(gui, alternating(preview.ingredients.getFirst(), 0), bx + 6, by + 44, 14);
        drawStack(gui, preview.target, bx + 77, by + 44, 14);
    }

    private void renderImagFusor(GuiGraphics gui, Preview preview, float x, float y, float size) {
        float bx = x + 8, by = y + 29;
        gui.blit(IMAG_FUSOR, Math.round(bx), Math.round(by), 118, 77,
                0, 0, 196, 128, 196, 128);
        drawFittedText(gui, Integer.toString(preview.phaseAmount), bx + 49, by + 9, 31, 9,
                0xFFFFFFFF, Align.CENTER);
        if (!preview.ingredients.isEmpty()) drawStack(gui, alternating(preview.ingredients.getFirst(), 0), bx + 11, by + 37, 19);
        drawStack(gui, preview.target, bx + 88, by + 37, 19);
    }

    private ItemStack alternating(Ingredient ingredient, int offset) {
        ItemStack[] stacks = ingredient.getItems();
        if (stacks.length == 0) return ItemStack.EMPTY;
        int index = Math.floorMod((int) (Util.getMillis() / 2000) + offset, stacks.length);
        return stacks[index];
    }

    private void drawStack(GuiGraphics gui, ItemStack stack, float x, float y, float size) {
        if (stack.isEmpty()) return;
        gui.pose().pushPose();
        gui.pose().translate(x, y, 30);
        float scale = size / 16f;
        gui.pose().scale(scale, scale, 1);
        gui.renderItem(stack, 0, 0);
        gui.renderItemDecorations(font, stack, 0, 0);
        gui.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        float vx = virtualX(mouseX), vy = virtualY(mouseY);
        float partY = (FRAME_HEIGHT - 220.5f) * .5f;
        float intro = (Util.getMillis() - openedAt) / 1000f;
        if ((!firstOpen || intro > 2.4f) && inside(vx, vy, 13.6f, partY + 7, 72, 207)) {
            int page = (int) ((vy - partY - 7) / 12);
            if (page >= 0 && page < PAGES.length) { selectPage(page); return true; }
        }
        if (selectedPage >= 0) {
            float rightX = 92, showX = rightX + 173.5f, rightY = partY;
            if (!previews.isEmpty()) {
                PreviewGroup group = previews.get(Mth.clamp(previewGroup, 0, previews.size() - 1));
                if (group.views.size() > 1 && inside(vx, vy, showX + 5, rightY + 41.75f, 12, 52)) {
                    previewView = Math.floorMod(previewView - 1, group.views.size()); return true;
                }
                if (group.views.size() > 1 && inside(vx, vy, showX + 140, rightY + 41.75f, 12, 52)) {
                    previewView = (previewView + 1) % group.views.size(); return true;
                }
                for (int index = 0; index < previews.size(); index++) {
                    if (inside(vx, vy, showX + 12 + index * 17, rightY + 120.75f, 18, 18)) {
                        previewGroup = index; previewView = 0; return true;
                    }
                }
            }
            if (unlocked(selectedPage) && inside(vx, vy, rightX + 158, rightY, 18, 220.5f)) {
                draggingScroll = true; updateScroll(vy, rightY); return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && draggingScroll && selectedPage >= 0) {
            updateScroll(virtualY(mouseY), (FRAME_HEIGHT - 220.5f) * .5f);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScroll) { draggingScroll = false; return true; }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && scrollBy(-(float) Math.signum(scrollY) * 20)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        float amount = switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> -20;
            case GLFW.GLFW_KEY_DOWN -> 20;
            case GLFW.GLFW_KEY_PAGE_UP -> -180;
            case GLFW.GLFW_KEY_PAGE_DOWN -> 180;
            case GLFW.GLFW_KEY_HOME -> -Float.MAX_VALUE;
            case GLFW.GLFW_KEY_END -> Float.MAX_VALUE;
            default -> 0;
        };
        if (amount != 0 && scrollBy(amount)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private float maxContentScroll() {
        return selectedPage < 0 || pageData[selectedPage] == null || !unlocked(selectedPage) ? 0
                : Math.max(0, pageData[selectedPage].contentHeight - 200.5f);
    }

    private boolean scrollBy(float amount) {
        float previous = scroll;
        scroll = Mth.clamp(scroll + amount, 0, maxContentScroll());
        return scroll != previous;
    }

    private void updateScroll(float virtualY, float rightY) {
        if (selectedPage < 0) return;
        float progress = Mth.clamp((virtualY - rightY - 28.5f) / 163f, 0, 1);
        scroll = maxContentScroll() * progress;
    }

    private void drawWrapped(GuiGraphics gui, String text, float x, float y, float width,
                             float size, int color, int step, int maxLines) {
        if (text == null || text.isBlank()) return;
        List<String> lines = ACLegacyFont.wrap(markdownText(text).getString(), Math.round(width), size, false);
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++)
            drawSequence(gui, lines.get(i), x, y + i * step, size, color);
    }

    private void drawSequence(GuiGraphics gui, String text, float x, float y, float size, int color) {
        ACLegacyFont.draw(gui, Component.literal(text), x, y, size, color,
                ACLegacyFont.LEFT, 0, false);
    }

    private void drawFittedText(GuiGraphics gui, String text, float x, float y, float width,
                                float size, int color, Align align) {
        int alignment = align == Align.RIGHT ? ACLegacyFont.RIGHT
                : align == Align.CENTER ? ACLegacyFont.CENTER : ACLegacyFont.LEFT;
        float anchor = align == Align.RIGHT ? x + width : align == Align.CENTER ? x + width * .5f : x;
        ACLegacyFont.draw(gui, Component.literal(text), anchor, y, size, color, alignment, width, false);
    }

    private static void drawTexture(GuiGraphics gui, ResourceLocation texture, float x, float y,
                                    int width, int height, int sourceWidth, int sourceHeight, float alpha) {
        gui.setColor(1, 1, 1, Mth.clamp(alpha, 0, 1));
        gui.blit(texture, Math.round(x), Math.round(y), width, height,
                0, 0, sourceWidth, sourceHeight, sourceWidth, sourceHeight);
        gui.setColor(1, 1, 1, 1);
    }

    private static float blend(float time, float start, float duration) {
        return Mth.clamp((time - start) / duration, 0, 1);
    }
    private static int withAlpha(int rgb, float alpha) {
        return Mth.clamp(Math.round(alpha * 255), 0, 255) << 24 | rgb & 0xFFFFFF;
    }
    private static boolean inside(float x, float y, float l, float t, float w, float h) {
        return x >= l && x <= l + w && y >= t && y <= t + h;
    }

    @Override public boolean isPauseScreen() { return false; }
    private enum Align { LEFT, CENTER, RIGHT }
}
