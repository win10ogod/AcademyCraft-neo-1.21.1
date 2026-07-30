package cn.academy;

import cn.academy.ability.AbilityRegistry;
import cn.academy.config.ACConfig;
import cn.academy.network.ACNetwork;
import cn.academy.registry.ACBlockEntities;
import cn.academy.registry.ACBlocks;
import cn.academy.registry.ACCreativeTabs;
import cn.academy.registry.ACDataComponents;
import cn.academy.registry.ACItems;
import cn.academy.registry.ACFluids;
import cn.academy.registry.ACEntities;
import cn.academy.registry.ACSounds;
import cn.academy.registry.ACMenus;
import cn.academy.registry.ACParticles;
import cn.academy.energy.ACCapabilities;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/** Main entry point for the native NeoForge port. */
@Mod(AcademyCraft.MOD_ID)
public final class AcademyCraft {
    public static final String MOD_ID = "academy";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AcademyCraft(IEventBus modBus, ModContainer container) {
        ACDataComponents.register(modBus);
        ACFluids.register(modBus);
        ACBlocks.register(modBus);
        ACBlockEntities.register(modBus);
        ACEntities.register(modBus);
        ACParticles.register(modBus);
        ACMenus.register(modBus);
        ACItems.register(modBus);
        ACSounds.register(modBus);
        ACCreativeTabs.register(modBus);
        modBus.addListener(ACNetwork::registerPayloads);
        modBus.addListener(ACCapabilities::register);
        container.registerConfig(ModConfig.Type.COMMON, ACConfig.SPEC);

        // Force deterministic construction before a player can be synchronized.
        AbilityRegistry.bootstrap();
        LOGGER.info("Starting AcademyCraft NeoForge port for Minecraft 1.21.1");
    }
}
