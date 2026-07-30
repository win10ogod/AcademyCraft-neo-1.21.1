package cn.academy.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/** 1.12.2 default controls, exposed through Minecraft's modern remapping screen. */
public final class ACKeyMappings {
    public static final String CATEGORY = "key.categories.academy";
    public static final KeyMapping ACTIVATE = keyboard("key.academy.activate", GLFW.GLFW_KEY_V);
    public static final KeyMapping SLOT_1 = mouse("key.academy.slot_1", GLFW.GLFW_MOUSE_BUTTON_LEFT);
    public static final KeyMapping SLOT_2 = mouse("key.academy.slot_2", GLFW.GLFW_MOUSE_BUTTON_RIGHT);
    public static final KeyMapping SLOT_3 = keyboard("key.academy.slot_3", GLFW.GLFW_KEY_R);
    public static final KeyMapping SLOT_4 = keyboard("key.academy.slot_4", GLFW.GLFW_KEY_F);
    public static final KeyMapping TERMINAL = keyboard("key.academy.terminal", GLFW.GLFW_KEY_LEFT_ALT);
    public static final KeyMapping EDIT_PRESET = keyboard("key.academy.edit_preset", GLFW.GLFW_KEY_N);
    public static final KeyMapping SWITCH_PRESET = keyboard("key.academy.switch_preset", GLFW.GLFW_KEY_C);

    public static final KeyMapping[] SLOTS = {SLOT_1, SLOT_2, SLOT_3, SLOT_4};

    private static KeyMapping keyboard(String name, int keyCode) {
        return new KeyMapping(name, InputConstants.Type.KEYSYM, keyCode, CATEGORY);
    }

    private static KeyMapping mouse(String name, int button) {
        return new KeyMapping(name, InputConstants.Type.MOUSE, button, CATEGORY);
    }

    private ACKeyMappings() {}
}
