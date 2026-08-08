package cn.academy.block;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyMachineBlockPropertyTest {
    @Test
    void onlyLegacyInvisibleTesrHostsUseNonOccludingProperties() {
        assertTrue(ACMachineBlock.usesLegacyBlockEntityModel(MachineKind.SOLAR_GENERATOR));
        assertTrue(ACMachineBlock.usesLegacyBlockEntityModel(MachineKind.PHASE_GENERATOR));
        assertTrue(ACMachineBlock.usesLegacyBlockEntityModel(MachineKind.WIND_GENERATOR));
        assertTrue(ACMachineBlock.usesLegacyBlockEntityModel(MachineKind.DEVELOPER_ADVANCED));

        assertFalse(ACMachineBlock.usesLegacyBlockEntityModel(MachineKind.NODE_BASIC));
        assertFalse(ACMachineBlock.usesLegacyBlockEntityModel(MachineKind.METAL_FORMER));
        assertFalse(ACMachineBlock.usesLegacyBlockEntityModel(MachineKind.ABILITY_INTERFERER));
    }
}
