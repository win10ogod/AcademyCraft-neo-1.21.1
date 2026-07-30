package cn.academy.client;

import cn.academy.AcademyCraft;
import cn.academy.ability.AbilityRegistry;
import cn.academy.ability.AbilitySkill;
import cn.academy.ability.AbilityState;
import cn.academy.client.screen.SkillTreeScreen;
import cn.academy.client.screen.TutorialScreen;
import cn.academy.client.screen.TerminalScreen;
import cn.academy.client.screen.TerminalInstallingScreen;
import cn.academy.client.screen.LocationTeleportScreen;
import cn.academy.client.screen.DeveloperScreen;
import cn.academy.client.render.ACVisualEffects;
import cn.academy.client.render.ACNotifications;
import cn.academy.client.sound.ACContextSounds;
import cn.academy.client.sound.ACMediaPlayer;
import cn.academy.registry.ACItems;
import net.minecraft.core.BlockPos;
import cn.academy.network.AbilityActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.InteractionHand;
import com.mojang.math.Axis;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class ACClientEvents {
    private static final boolean[] SLOT_HELD = new boolean[4];
    private static boolean activateHeld;
    private static int activateTicks;
    private static int lastMovementInput;
    private static final ResourceLocation CP_BACK = guiTexture("cpbar/back_normal.png");
    private static final ResourceLocation CP_BACK_OVERLOAD = guiTexture("cpbar/back_overload.png");
    private static final ResourceLocation CP_FILL = guiTexture("cpbar/cp.png");
    private static final ResourceLocation OVERLOAD_FILL = guiTexture("cpbar/front_overload.png");
    private static final ResourceLocation OVERLOAD_HIGHLIGHT = guiTexture("cpbar/highlight_overload.png");
    private static final ResourceLocation OVERLOADED = guiTexture("cpbar/overloaded.png");
    private static final ResourceLocation KEY_HINT_BACK = guiTexture("key_hint/back.png");

    private static ResourceLocation guiTexture(String path) {
        return ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "textures/guis/" + path);
    }
    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            ACContextSounds.stopAll();
            ACMediaPlayer.stop();
            ACFrequencyTransmitter.stop();
            return;
        }
        net.minecraft.nbt.ListTag soundQueue = minecraft.player.getPersistentData()
                .getList("academy:context_sound_queue", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (net.minecraft.nbt.Tag raw : soundQueue) {
            net.minecraft.nbt.CompoundTag sound = (net.minecraft.nbt.CompoundTag) raw;
            if (sound.hasUUID("Entity")) ACContextSounds.handle(sound.getUUID("Entity"), sound.getString("Sound"),
                    sound.getBoolean("Start"), sound.getFloat("Volume"));
        }
        if (!soundQueue.isEmpty()) minecraft.player.getPersistentData().remove("academy:context_sound_queue");
        ACVisualEffects.clientTick(minecraft);
        ACNotifications.tick();
        ACMediaPlayer.tick();
        ACFrequencyTransmitter.tick();
        String requestedScreen = minecraft.player.getPersistentData().getString("academy:open_screen");
        if (!requestedScreen.isEmpty()) {
            abortHeldSlots();
            minecraft.player.getPersistentData().remove("academy:open_screen");
            if (requestedScreen.startsWith("notify:app:")) {
                ACNotifications.showAppInstalled(requestedScreen.substring("notify:app:".length()));
            }
            else if (requestedScreen.equals("close")) minecraft.setScreen(null);
            else if (requestedScreen.equals("tutorial")) minecraft.setScreen(new TutorialScreen());
            else if (requestedScreen.equals("skills")) minecraft.setScreen(new SkillTreeScreen());
            else if (requestedScreen.equals("terminal")) minecraft.setScreen(new TerminalScreen());
            else if (requestedScreen.equals("terminal_install")) minecraft.setScreen(new TerminalInstallingScreen());
            else if (requestedScreen.equals("locations")) minecraft.setScreen(new LocationTeleportScreen());
            else if (requestedScreen.equals("developer:portable")) minecraft.setScreen(new DeveloperScreen(BlockPos.ZERO, true));
            else if (requestedScreen.startsWith("developer:")) {
                String[] parts = requestedScreen.split(":");
                if (parts.length == 4) try {
                    minecraft.setScreen(new DeveloperScreen(new BlockPos(Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2]), Integer.parseInt(parts[3])), false));
                } catch (NumberFormatException ignored) { }
            }
            return;
        }
        if (minecraft.screen != null || ACFrequencyTransmitter.active()) {
            abortHeldSlots();
            return;
        }
        boolean activateDown = ACKeyMappings.ACTIVATE.isDown();
        if (activateDown) {
            if (!activateHeld) activateTicks = 0;
            activateHeld = true;
            activateTicks++;
        } else if (activateHeld) {
            if (activateTicks <= 6) PacketDistributor.sendToServer(AbilityActionPayload.toggle());
            activateHeld = false;
            activateTicks = 0;
        }
        AbilityState controlState = AbilityState.load(minecraft.player);
        boolean controlsEnabled = controlState.active() && !controlState.interfered() && !controlState.overloadLocked();
        int movement = 0;
        if (controlsEnabled) {
            if (minecraft.options.keyUp.isDown()) movement |= 1;
            if (minecraft.options.keyDown.isDown()) movement |= 2;
            if (minecraft.options.keyLeft.isDown()) movement |= 4;
            if (minecraft.options.keyRight.isDown()) movement |= 8;
        }
        sendMovementInput(movement);
        for (int slot = 0; slot < ACKeyMappings.SLOTS.length; slot++) {
            boolean physicalDown = ACKeyMappings.SLOTS[slot].isDown();
            if (!controlsEnabled) {
                if (SLOT_HELD[slot]) PacketDistributor.sendToServer(AbilityActionPayload.keyAbort(slot));
                SLOT_HELD[slot] = false;
            } else {
                if (physicalDown && !SLOT_HELD[slot]) PacketDistributor.sendToServer(AbilityActionPayload.keyDown(slot));
                else if (!physicalDown && SLOT_HELD[slot]) PacketDistributor.sendToServer(AbilityActionPayload.keyUp(slot));
                SLOT_HELD[slot] = physicalDown;
            }
        }
        if (ACKeyMappings.SWITCH_PRESET.consumeClick()) {
            AbilityState state = AbilityState.load(minecraft.player);
            PacketDistributor.sendToServer(AbilityActionPayload.switchPreset((state.currentPreset() + 1) % 4));
        }
        if (ACKeyMappings.EDIT_PRESET.consumeClick()) {
            AbilityState state = AbilityState.load(minecraft.player);
            if (state.hasCategory()) minecraft.setScreen(new SkillTreeScreen());
        }
        if (ACKeyMappings.TERMINAL.consumeClick()) {            AbilityState state = AbilityState.load(minecraft.player);
            if (state.terminalInstalled()) {
                minecraft.setScreen(new TerminalScreen());
            } else {
                minecraft.player.displayClientMessage(Component.translatable("ac.terminal.notinstalled"), true);
            }
        }
    }

    @SubscribeEvent
    public static void renderAbilityHand(RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || event.getHand() != InteractionHand.MAIN_HAND) return;
        net.minecraft.nbt.CompoundTag contexts = minecraft.player.getPersistentData()
                .getCompound("academy:ability_contexts");
        for (String key : contexts.getAllKeys()) {
            net.minecraft.nbt.CompoundTag context = contexts.getCompound(key);
            String skill = context.getString("Skill");
            if (!skill.endsWith(".dir_shock") && !skill.endsWith(".dir_blast")) continue;
            float progress = Math.min(1, context.getInt("Ticks") / 6f);
            event.getPoseStack().translate(-.12f * progress, .10f * progress, -.22f * progress);
            event.getPoseStack().mulPose(Axis.XP.rotationDegrees(-32 * progress));
            event.getPoseStack().mulPose(Axis.ZP.rotationDegrees(18 * progress));
            return;
        }
    }

    @SubscribeEvent
    public static void mouseButton(InputEvent.MouseButton.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ACFrequencyTransmitter.active() || minecraft.screen != null
                || (event.getButton() != org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT
                && event.getButton() != org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT)) return;
        if (event.getAction() == org.lwjgl.glfw.GLFW.GLFW_PRESS
                && event.getButton() == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT)
            ACFrequencyTransmitter.rightClickTarget();
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void interactionKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || (!event.isAttack() && !event.isUseItem())) return;
        AbilityState state = AbilityState.load(minecraft.player);
        if (event.isUseItem() && (minecraft.player.getMainHandItem().is(ACItems.COIN.get())
                || minecraft.player.getMainHandItem().is(ACItems.SILBARN.get())
                || minecraft.player.getMainHandItem().is(ACItems.MAG_HOOK.get()))) return;
        if (state.active() && !state.interfered()) {
            boolean claimed = false;
            for (int slot = 0; slot < ACKeyMappings.SLOTS.length; slot++) {
                if (AbilityRegistry.skill(state.preset(slot)) == null) continue;
                if (event.isAttack() && ACKeyMappings.SLOTS[slot].same(minecraft.options.keyAttack)
                        || event.isUseItem() && ACKeyMappings.SLOTS[slot].same(minecraft.options.keyUse)) {
                    claimed = true;
                    break;
                }
            }
            if (claimed) {
                event.setSwingHand(false);
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void mouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || event.getScrollDeltaY() == 0) return;
        AbilityState state = AbilityState.load(minecraft.player);
        if (!state.mouseWheelTeleport()) return;
        for (int slot = 0; slot < SLOT_HELD.length; slot++) {
            AbilitySkill skill = AbilityRegistry.skill(state.preset(slot));
            if (SLOT_HELD[slot] && skill != null && skill.name().equals("penetrate_teleport")) {
                PacketDistributor.sendToServer(AbilityActionPayload.mouseWheel(event.getScrollDeltaY() > 0 ? 1 : -1));
                event.setCanceled(true);
                return;
            }
        }
    }

    private static void sendMovementInput(int movement) {
        if (movement == lastMovementInput) return;
        lastMovementInput = movement;
        PacketDistributor.sendToServer(AbilityActionPayload.movementInput(movement));
    }

    private static void abortHeldSlots() {
        activateHeld = false;
        activateTicks = 0;
        sendMovementInput(0);
        for (int slot = 0; slot < SLOT_HELD.length; slot++) {
            if (SLOT_HELD[slot]) {
                PacketDistributor.sendToServer(AbilityActionPayload.keyAbort(slot));
                SLOT_HELD[slot] = false;
            }
        }
    }

    @SubscribeEvent
    public static void renderLevelEffects(RenderLevelStageEvent event) {
        ACVisualEffects.render(event);
    }

    @SubscribeEvent
    public static void renderHud(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) return;
        GuiGraphics gui = event.getGuiGraphics();
        ACNotifications.render(gui, minecraft, event.getPartialTick().getGameTimeDeltaPartialTick(true));
        ACMediaPlayer.renderHud(gui, minecraft);
        ACFrequencyTransmitter.render(gui, minecraft);
        AbilityState state = AbilityState.load(minecraft.player);
        if (!state.hasCategory()) return;
        int barWidth = 193, barHeight = 29;
        int jitterX = state.interfered() ? (int) ((minecraft.level.getGameTime() * 17 % 5) - 2) : 0;
        int jitterY = state.interfered() ? (int) ((minecraft.level.getGameTime() * 11 % 3) - 1) : 0;
        int x = gui.guiWidth() - barWidth - 12 + jitterX;
        int y = 12 + jitterY;
        int cpWidth = state.maxCp() <= 0 ? 0 : Math.round(barWidth * state.cp() / state.maxCp());
        int overloadWidth = state.maxOverload() <= 0 ? 0 : Math.round(barWidth * state.overload() / state.maxOverload());
        float alpha = state.active() ? 1f : .38f;
        gui.setColor(1, 1, 1, alpha);
        gui.blit(state.overloadLocked() ? CP_BACK_OVERLOAD : CP_BACK, x, y, barWidth, barHeight,
                0, 0, 964, 147, 964, 147);
        if (cpWidth > 0) {
            gui.enableScissor(x, y, x + cpWidth, y + barHeight);
            gui.blit(CP_FILL, x, y, barWidth, barHeight, 0, 0, 964, 147, 964, 147);
            gui.disableScissor();
        }
        if (overloadWidth > 0) {
            gui.enableScissor(x, y, x + overloadWidth, y + barHeight);
            gui.blit(OVERLOAD_FILL, x, y, barWidth, barHeight, 0, 0, 974, 147, 974, 147);
            gui.disableScissor();
        }
        if (state.overloadLocked()) {
            gui.blit(OVERLOAD_HIGHLIGHT, x, y, barWidth, barHeight, 0, 0, 964, 147, 964, 147);
            gui.blit(OVERLOADED, x + barWidth - 66, y + 24, 64, 16, 0, 0, 256, 64, 256, 64);
        }
        gui.setColor(1, 1, 1, 1);
        var category = AbilityRegistry.category(state.category());
        if (category != null) {
            int iconSize = category.id().equals("vecmanip") ? 32 : 64;
            gui.blit(category.icon(), x - 24, y + 2, 22, 22,
                    0, 0, iconSize, iconSize, iconSize, iconSize);
        }
        Component level = Component.translatable("ac.ability.level" + state.level());
        String cpText = String.format("CP %.0f/%.0f", state.cp(), state.maxCp());
        gui.drawString(minecraft.font, Component.literal("P" + (state.currentPreset() + 1) + "  ").append(level),
                x + 5, y + 4, state.active() ? 0xFFE8FAFF : 0xFF87979C, true);
        if (activateHeld) gui.drawString(minecraft.font, cpText,
                x + barWidth - minecraft.font.width(cpText) - 5, y + 4, 0xFFD9F6FF, true);

        // The original key-hint area remains visible at the lower left while the CP bar stays at the upper right.
        gui.setColor(.72f, .9f, 1f, state.active() ? .78f : .38f);
        gui.blit(KEY_HINT_BACK, 2, gui.guiHeight() - 55, 174, 56, 0, 0, 256, 83, 256, 83);
        gui.setColor(1, 1, 1, 1);
        net.minecraft.nbt.CompoundTag contexts = minecraft.player.getPersistentData()
                .getCompound("academy:ability_contexts");
        for (int slot = 0; slot < 4; slot++) {
            String id = state.preset(slot);
            AbilitySkill skill = AbilityRegistry.skill(id);
            String name = skill == null ? "-" : skill.displayName().getString();
            int cooldown = skill == null ? 0 : state.cooldown(skill.id());
            int sy = gui.guiHeight() - 45 + slot * 10;
            net.minecraft.nbt.CompoundTag context = contexts.getCompound(Integer.toString(slot));
            boolean contextActive = !context.isEmpty() && context.getString("Skill").equals(id);
            String suffix = cooldown > 0 ? String.format("  %.1fs", cooldown / 20f) : "";
            if (contextActive) {
                int target = context.getInt("Target");
                int ticks = context.getInt("Ticks");
                int fill = target > 0 ? Math.min(168, Math.round(168f * ticks / target)) : 168;
                int color = context.getInt("State") == 1 ? 0x8065C8FF : 0x806FFFB4;
                gui.fill(5, sy - 1, 5 + fill, sy + 8, color);
                suffix = "  " + Component.translatable(context.getInt("State") == 1
                        ? "ac.ability.context.charging" : "ac.ability.context.active").getString();
            }
            String text = ACKeyMappings.SLOTS[slot].getTranslatedKeyMessage().getString() + "  " + name + suffix;
            gui.drawString(minecraft.font, text, 7, sy,
                    !state.active() ? 0xFF788489 : contextActive ? 0xFFFFFFFF
                            : cooldown > 0 ? 0xFFFFB060 : 0xFFE9FAFF, true);
        }
    }

    private ACClientEvents() {}
}
