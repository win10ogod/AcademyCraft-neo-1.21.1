package cn.academy.client.render;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyMachineTransformTest {
    @Test
    void renderBlockMultiUsesLegacyDirectionAngles() {
        assertEquals(180, ACMachineRenderer.legacyRotation(Direction.NORTH));
        assertEquals(0, ACMachineRenderer.legacyRotation(Direction.SOUTH));
        assertEquals(270, ACMachineRenderer.legacyRotation(Direction.WEST));
        assertEquals(90, ACMachineRenderer.legacyRotation(Direction.EAST));
    }

    @Test
    void renderBlockMultiUsesLegacyPivotOffsetsAndRotatedCentres() {
        assertArrayEquals(new double[]{.5, .4}, ACMachineRenderer.legacyPivot(Direction.NORTH, .5, .4));
        assertArrayEquals(new double[]{.5, .6}, ACMachineRenderer.legacyPivot(Direction.SOUTH, .5, .4));
        assertArrayEquals(new double[]{.4, .5}, ACMachineRenderer.legacyPivot(Direction.WEST, .5, .4));
        assertArrayEquals(new double[]{.6, .5}, ACMachineRenderer.legacyPivot(Direction.EAST, .5, .4));

        assertArrayEquals(new double[]{1, 1}, ACMachineRenderer.legacyPivot(Direction.NORTH, 1, 1));
        assertArrayEquals(new double[]{0, 0}, ACMachineRenderer.legacyPivot(Direction.SOUTH, 1, 1));
        assertArrayEquals(new double[]{1, 0}, ACMachineRenderer.legacyPivot(Direction.WEST, 1, 1));
        assertArrayEquals(new double[]{0, 1}, ACMachineRenderer.legacyPivot(Direction.EAST, 1, 1));
    }
}
