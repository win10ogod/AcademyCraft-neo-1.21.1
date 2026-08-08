package cn.academy.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ACClientModEventsTest {
    @Test
    void imagPhaseFluidKeepsTheLegacyBlackBaseWithoutTint() {
        assertEquals("academy:block/black", ACClientModEvents.IMAG_PHASE_FLUID_TEXTURE.toString());
        assertEquals(0xFFFFFFFF, ACClientModEvents.IMAG_PHASE_FLUID_TINT);
    }
}
