package cn.academy.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnergyItemVisualTest {
    @Test
    void legacyDamageThresholdsSelectEmptyHalfAndFullIcons() {
        assertEquals(0, EnergyItem.legacyModelStage(0, 10_000));
        assertEquals(0, EnergyItem.legacyModelStage(1_923, 10_000));
        assertEquals(1, EnergyItem.legacyModelStage(1_924, 10_000));
        assertEquals(1, EnergyItem.legacyModelStage(8_076, 10_000));
        assertEquals(2, EnergyItem.legacyModelStage(8_077, 10_000));
        assertEquals(2, EnergyItem.legacyModelStage(10_000, 10_000));
    }
}
