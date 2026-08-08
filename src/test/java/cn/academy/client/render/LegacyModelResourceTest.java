package cn.academy.client.render;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyModelResourceTest {
    private static final Set<String> MACHINE_BLOCKS = Set.of(
            "ability_interferer", "ac_rf_input", "ac_rf_output", "cat_engine", "dev_advanced",
            "dev_normal", "eu_input", "eu_output", "imag_fusor", "matrix", "metal_former",
            "node_advanced", "node_basic", "node_standard", "phase_gen", "solar_gen",
            "windgen_base", "windgen_main", "windgen_pillar");

    @Test
    void legacyMachineItemsRemainGeneratedSprites() throws IOException {
        Map<String, String> expectedTextures = Map.ofEntries(
                Map.entry("cat_engine", "cat_engine"),
                Map.entry("dev_advanced", "dev_advanced"),
                Map.entry("dev_normal", "dev_normal"),
                Map.entry("imag_phase", "phase_liquid"),
                Map.entry("matrix", "matrix"),
                Map.entry("phase_gen", "phase_gen"),
                Map.entry("solar_gen", "solar_gen"),
                Map.entry("windgen_base", "windgen_base"),
                Map.entry("windgen_main", "windgen_main"),
                Map.entry("windgen_pillar", "windgen_pillar"));

        for (var expected : expectedTextures.entrySet()) {
            JsonObject model = model(expected.getKey());
            assertEquals("minecraft:item/generated", model.get("parent").getAsString(), expected.getKey());
            assertEquals("academy:block/" + expected.getValue(),
                    model.getAsJsonObject("textures").get("layer0").getAsString(), expected.getKey());
        }
    }

    @Test
    void onlyLegacyTeisrItemsUseTransformFreeBuiltinModels() throws IOException {
        JsonObject coin = model("coin");
        assertEquals("minecraft:builtin/entity", coin.get("parent").getAsString());
        assertFalse(coin.has("display"), "coin transforms must stay in ACItemRenderer");

        for (String name : Set.of("developer_portable", "developer_portable_half",
                "developer_portable_full", "mag_hook", "silbarn")) {
            JsonObject wrapper = model(name);
            assertEquals("neoforge:separate_transforms", wrapper.get("loader").getAsString(), name);
            assertEquals("minecraft:builtin/entity",
                    wrapper.getAsJsonObject("base").get("parent").getAsString(), name);
            JsonObject gui = wrapper.getAsJsonObject("perspectives").getAsJsonObject("gui");
            assertEquals("minecraft:item/generated", gui.get("parent").getAsString(), name);
            assertFalse(wrapper.getAsJsonObject("base").has("display"),
                    name + " non-GUI transforms must stay in ACItemRenderer");
        }
        assertEquals(2, model("developer_portable").getAsJsonArray("overrides").size());

        JsonObject matter = model("matter_unit");
        assertEquals("minecraft:item/generated", matter.get("parent").getAsString());
        assertEquals("academy:item/matter_unit_phase_liquid_0", matter.getAsJsonArray("overrides")
                .get(0).getAsJsonObject().get("model").getAsString());
        assertEquals(3, model("matter_unit_phase_liquid_0").getAsJsonArray("overrides").size());
        assertEquals("minecraft:item/generated", model("terminal_installer").get("parent").getAsString());
        assertEquals("minecraft:item/generated", model("windgen_fan").get("parent").getAsString());
    }

    @Test
    void everyRegisteredItemHasItsLegacyModelKindAndTexture() throws IOException {
        Map<String, String> generated = Map.ofEntries(
                Map.entry("app_freq_transmitter", "app_freq_transmitter"),
                Map.entry("app_media_player", "app_media_player"),
                Map.entry("app_skill_tree", "app_skill_tree"),
                Map.entry("brain_component", "brain_component"), Map.entry("calc_chip", "calc_chip"),
                Map.entry("constraint_ingot", "constraint_ingot"), Map.entry("constraint_plate", "constraint_plate"),
                Map.entry("crystal_low", "crystal_low"), Map.entry("crystal_normal", "crystal_normal"),
                Map.entry("crystal_pure", "crystal_pure"), Map.entry("data_chip", "data_chip"),
                Map.entry("energy_convert_component", "energy_convert_component"),
                Map.entry("energy_unit", "energy_unit_empty"),
                Map.entry("imag_silicon_ingot", "imag_silicon_ingot"),
                Map.entry("imag_silicon_piece", "imag_silicon_piece"),
                Map.entry("induction_factor", "factor_electromaster"), Map.entry("info_component", "info_component"),
                Map.entry("logo", "logo"), Map.entry("magnetic_coil", "magnetic_coil"),
                Map.entry("mat_core", "mat_core_0"), Map.entry("matter_unit", "matter_unit"),
                Map.entry("media_item", "media_sisters_noise"), Map.entry("needle", "needle"),
                Map.entry("reinforced_iron_plate", "reinforced_iron_plate"),
                Map.entry("reso_crystal", "reso_crystal"), Map.entry("resonance_component", "resonance_component"),
                Map.entry("terminal_installer", "terminal_installer"), Map.entry("tutorial", "tutorial"),
                Map.entry("wafer", "wafer"), Map.entry("windgen_fan", "windgen_fan"));
        Map<String, String> blockModels = Map.ofEntries(
                Map.entry("ability_interferer", "ability_interferer"),
                Map.entry("constraint_metal", "constraint_metal"), Map.entry("crystal_ore", "crystal_ore"),
                Map.entry("imag_fusor", "imag_fusor"), Map.entry("imagsil_ore", "imagsil_ore"),
                Map.entry("machine_frame", "machine_frame"), Map.entry("metal_former", "metal_former"),
                Map.entry("node_advanced", "node_advanced"), Map.entry("node_basic", "node_basic"),
                Map.entry("node_standard", "node_standard"), Map.entry("reso_ore", "reso_ore"),
                Map.entry("ac_rf_input", "ac_rf_input"), Map.entry("ac_rf_output", "ac_rf_output"),
                Map.entry("eu_input", "eu_input"), Map.entry("eu_output", "eu_output"));
        Map<String, String> flatBlocks = Map.ofEntries(
                Map.entry("cat_engine", "cat_engine"), Map.entry("dev_advanced", "dev_advanced"),
                Map.entry("dev_normal", "dev_normal"), Map.entry("imag_phase", "phase_liquid"),
                Map.entry("matrix", "matrix"), Map.entry("phase_gen", "phase_gen"),
                Map.entry("solar_gen", "solar_gen"), Map.entry("windgen_base", "windgen_base"),
                Map.entry("windgen_main", "windgen_main"), Map.entry("windgen_pillar", "windgen_pillar"));
        Set<String> teisr = Set.of("coin", "developer_portable", "mag_hook", "silbarn");

        for (var expected : generated.entrySet()) {
            JsonObject value = model(expected.getKey());
            assertEquals("minecraft:item/generated", value.get("parent").getAsString(), expected.getKey());
            assertEquals("academy:item/" + expected.getValue(),
                    value.getAsJsonObject("textures").get("layer0").getAsString(), expected.getKey());
            assertNotNull(LegacyModelResourceTest.class.getResource(
                    "/assets/academy/textures/item/" + expected.getValue() + ".png"), expected.getKey());
        }
        for (var expected : blockModels.entrySet()) {
            assertEquals("academy:block/" + expected.getValue(),
                    model(expected.getKey()).get("parent").getAsString(), expected.getKey());
        }
        for (var expected : flatBlocks.entrySet()) {
            JsonObject value = model(expected.getKey());
            assertEquals("minecraft:item/generated", value.get("parent").getAsString(), expected.getKey());
            assertEquals("academy:block/" + expected.getValue(),
                    value.getAsJsonObject("textures").get("layer0").getAsString(), expected.getKey());
            assertNotNull(LegacyModelResourceTest.class.getResource(
                    "/assets/academy/textures/block/" + expected.getValue() + ".png"), expected.getKey());
        }

        Set<String> covered = new HashSet<>(generated.keySet());
        covered.addAll(blockModels.keySet());
        covered.addAll(flatBlocks.keySet());
        covered.addAll(teisr);
        assertEquals(Set.of(
                "app_freq_transmitter", "app_media_player", "app_skill_tree", "brain_component", "calc_chip",
                "coin", "constraint_ingot", "constraint_plate", "crystal_low", "crystal_normal", "crystal_pure",
                "data_chip", "developer_portable", "energy_convert_component", "energy_unit", "imag_silicon_ingot",
                "imag_silicon_piece", "induction_factor", "info_component", "logo", "mag_hook", "magnetic_coil",
                "mat_core", "matter_unit", "media_item", "needle", "reinforced_iron_plate", "reso_crystal",
                "resonance_component", "silbarn", "terminal_installer", "tutorial", "wafer", "windgen_fan",
                "ability_interferer", "cat_engine", "constraint_metal", "crystal_ore", "dev_advanced", "dev_normal",
                "imag_fusor", "imag_phase", "imagsil_ore", "machine_frame", "matrix", "metal_former",
                "node_advanced", "node_basic", "node_standard", "phase_gen", "reso_ore", "solar_gen",
                "windgen_base", "windgen_main", "windgen_pillar", "ac_rf_input", "ac_rf_output", "eu_input", "eu_output"),
                covered);
    }

    @Test
    void nodeModelsKeepConnectionAndEnergyAsIndependentVisualProperties() throws IOException {
        for (String node : Set.of("node_basic", "node_standard", "node_advanced")) {
            JsonObject variants = blockstate(node).getAsJsonObject("variants");
            assertEquals(40, variants.size(), node);
            for (boolean connected : new boolean[]{false, true}) {
                for (String facing : Set.of("north", "east", "south", "west")) {
                    for (int stage = 0; stage <= 4; stage++) {
                        assertTrue(variants.has("connected=" + connected + ",facing=" + facing
                                + ",visual_stage=" + stage), node);
                    }
                }
            }
            assertEquals("academy:block/node_top_1", blockModel(node + "_connected_stage_0")
                    .getAsJsonObject("textures").get("up").getAsString(), node);
            assertEquals("academy:block/node_top_0", blockModel(node + "_disconnected_stage_4")
                    .getAsJsonObject("textures").get("up").getAsString(), node);
        }
    }

    @Test
    void everyMachineBlockstateCoversEveryRuntimePropertyCombination() throws IOException {
        for (String block : MACHINE_BLOCKS) {
            JsonObject variants = blockstate(block).getAsJsonObject("variants");
            Set<String> expected = new HashSet<>();
            for (boolean connected : new boolean[]{false, true}) {
                for (String facing : Set.of("north", "east", "south", "west")) {
                    for (int stage = 0; stage <= 4; stage++) {
                        expected.add("connected=" + connected + ",facing=" + facing
                                + ",visual_stage=" + stage);
                    }
                }
            }
            assertEquals(expected, variants.keySet(), block);
        }
    }

    @Test
    void everyRegisteredBlockstateResolvesItsModels() throws IOException {
        Set<String> blocks = Set.of(
                "ability_interferer", "ac_rf_input", "ac_rf_output", "cat_engine", "constraint_metal",
                "crystal_ore", "dev_advanced", "dev_normal", "eu_input", "eu_output", "imag_fusor",
                "imag_phase", "imagsil_ore", "machine_frame", "matrix", "metal_former", "node_advanced",
                "node_basic", "node_standard", "phase_gen", "reso_ore", "solar_gen", "windgen_base",
                "windgen_main", "windgen_pillar", "multiblock_part");
        for (String block : blocks) {
            JsonObject variants = blockstate(block).getAsJsonObject("variants");
            assertFalse(variants.isEmpty(), block);
            for (var variant : variants.entrySet()) {
                JsonObject definition = variant.getValue().getAsJsonObject();
                String reference = definition.get("model").getAsString();
                assertTrue(reference.startsWith("academy:block/"), block + " -> " + reference);
                String path = "/assets/academy/models/block/"
                        + reference.substring("academy:block/".length()) + ".json";
                assertNotNull(LegacyModelResourceTest.class.getResource(path), block + " -> " + path);
            }
        }
    }

    @Test
    void everyLegacyObjAndItsTextureArePackaged() {
        Set<String> models = Set.of("solar", "ip_gen", "matrix", "windgen_base", "windgen_pillar",
                "windgen_main", "windgen_fan", "developer_normal", "developer_advanced",
                "developer_portable", "terminal_installer", "maghook", "maghook_open", "silbarn");
        Set<String> textures = Set.of("solar", "matrix", "windgen_base", "windgen_base_disabled",
                "windgen_pillar", "windgen_main", "windgen_fan", "developer_normal", "developer_advanced",
                "developer_portable", "terminal_installer", "maghook", "silbarn",
                "ip_gen0", "ip_gen1", "ip_gen2", "ip_gen3", "ip_gen4");
        for (String name : models) assertNotNull(LegacyModelResourceTest.class.getResource(
                "/assets/academy/models/" + name + ".obj"), name);
        for (String name : textures) assertNotNull(LegacyModelResourceTest.class.getResource(
                "/assets/academy/textures/models/" + name + ".png"), name);
    }

    private static JsonObject model(String name) throws IOException {
        return resource("/assets/academy/models/item/" + name + ".json");
    }

    private static JsonObject blockModel(String name) throws IOException {
        return resource("/assets/academy/models/block/" + name + ".json");
    }

    private static JsonObject blockstate(String name) throws IOException {
        return resource("/assets/academy/blockstates/" + name + ".json");
    }

    private static JsonObject resource(String path) throws IOException {
        try (InputStream stream = LegacyModelResourceTest.class.getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing model resource " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
