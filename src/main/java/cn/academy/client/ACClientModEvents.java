package cn.academy.client;

import cn.academy.AcademyCraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.minecraft.resources.ResourceLocation;
import cn.academy.registry.ACFluids;
import cn.academy.registry.ACEntities;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import cn.academy.registry.ACMenus;
import cn.academy.client.screen.ACMachineScreen;
import cn.academy.client.render.ACMachineRenderer;
import cn.academy.client.render.ACItemRenderer;
import cn.academy.client.render.ACThrownItemRenderer;
import cn.academy.client.render.ACElectronBallRenderer;
import cn.academy.client.render.ACShaders;
import cn.academy.client.render.ACLegacyParticle;
import cn.academy.client.render.ACLegacyFont;
import cn.academy.client.render.ACSkillProgressTextures;
import cn.academy.client.render.ACGuiTextures;
import cn.academy.client.render.ACObjModel;
import cn.academy.registry.ACBlockEntities;
import cn.academy.registry.ACItems;
import cn.academy.registry.ACParticles;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class ACClientModEvents {
    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) {
        ACShaders.register(event);
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            ACObjModel.clearCache();
            ACLegacyFont.clear();
            ACSkillProgressTextures.clear();
            ACGuiTextures.clear();
            for (String model : new String[]{"solar", "ip_gen", "matrix", "windgen_base", "windgen_pillar",
                    "windgen_main", "windgen_fan", "developer_normal", "developer_advanced", "developer_portable",
                    "terminal_installer", "maghook", "maghook_open", "silbarn"}) ACObjModel.get(model);
        });
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ACParticles.ARC.get(), sprites -> new ACLegacyParticle.Provider(sprites, ACLegacyParticle.Style.ARC));
        event.registerSpriteSet(ACParticles.MELTDOWNER.get(), sprites -> new ACLegacyParticle.Provider(sprites, ACLegacyParticle.Style.MELTDOWNER));
        event.registerSpriteSet(ACParticles.TELEPORT.get(), sprites -> new ACLegacyParticle.Provider(sprites, ACLegacyParticle.Style.TELEPORT));
        event.registerSpriteSet(ACParticles.VECTOR.get(), sprites -> new ACLegacyParticle.Provider(sprites, ACLegacyParticle.Style.VECTOR));
        event.registerSpriteSet(ACParticles.SILBARN_FRAGMENT.get(), sprites -> new ACLegacyParticle.Provider(sprites, ACLegacyParticle.Style.SILBARN));
    }

    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ACMenus.MACHINE.get(), ACMachineScreen::new);
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ACEntities.THROWN_ITEM.get(), ACThrownItemRenderer::new);
        event.registerEntityRenderer(ACEntities.ELECTRON_BALL.get(), ACElectronBallRenderer::new);
        event.registerBlockEntityRenderer(ACBlockEntities.MACHINE.get(), ACMachineRenderer::new);
    }

    @SubscribeEvent
    public static void registerFluidRendering(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            private static final ResourceLocation TEXTURE =
                    ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "block/phase_liquid");

            @Override public ResourceLocation getStillTexture() { return TEXTURE; }
            @Override public ResourceLocation getFlowingTexture() { return TEXTURE; }
            @Override public int getTintColor() { return 0xCC66DDEB; }
        }, ACFluids.IMAG_PHASE_TYPE.get());

        IClientItemExtensions legacyModels = new IClientItemExtensions() {
            private final ACItemRenderer renderer = new ACItemRenderer();
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() { return renderer; }
            @Override public boolean shouldBobAsEntity(net.minecraft.world.item.ItemStack stack) { return false; }
        };
        event.registerItem(legacyModels,
                ACItems.SOLAR_GEN.get(), ACItems.PHASE_GEN.get(), ACItems.MATRIX.get(), ACItems.CAT_ENGINE.get(),
                ACItems.WINDGEN_BASE.get(), ACItems.WINDGEN_PILLAR.get(), ACItems.WINDGEN_MAIN.get(),
                ACItems.DEV_NORMAL.get(), ACItems.DEV_ADVANCED.get(), ACItems.DEVELOPER_PORTABLE.get(),
                ACItems.TERMINAL_INSTALLER.get(), ACItems.MAG_HOOK.get(), ACItems.SILBARN.get(),
                ACItems.WINDGEN_FAN.get(), ACItems.MATTER_UNIT.get(), ACItems.COIN.get());
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(ACKeyMappings.ACTIVATE);
        for (var key : ACKeyMappings.SLOTS) event.register(key);
        event.register(ACKeyMappings.TERMINAL);
        event.register(ACKeyMappings.EDIT_PRESET);
        event.register(ACKeyMappings.SWITCH_PRESET);
    }

    private ACClientModEvents() {}
}
