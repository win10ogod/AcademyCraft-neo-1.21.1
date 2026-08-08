package cn.academy.client.render;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ACObjModelTest {
    @Test
    void ignoresObjNormalsAndRebuildsLambdaLibSmoothedNormalsByPositionAndUv() throws Exception {
        ACObjModel model = ACObjModel.parse(new StringReader("""
                v 0 0 0
                v 1 0 0
                v 0 1 0
                v 0 0 1
                vt 0 0
                vt 1 0
                vt 0 1
                vt 1 1
                vn -1 0 0
                f 1/1/1 2/2/1 3/3/1
                f 1/1/1 4/4/1 2/2/1
                """));

        assertEquals(2, model.triangleCount());
        float diagonal = (float) (1 / Math.sqrt(2));
        assertVector(new Vector3f(0, diagonal, diagonal), model.legacyNormal(0, 0));
        assertVector(new Vector3f(0, diagonal, diagonal), model.legacyNormal(1, 1));
        assertVector(new Vector3f(0, 0, 1), model.legacyNormal(2, 2));
        assertVector(new Vector3f(0, 1, 0), model.legacyNormal(3, 3));
    }

    private static void assertVector(Vector3f expected, Vector3f actual) {
        assertEquals(expected.x, actual.x, 1e-6f);
        assertEquals(expected.y, actual.y, 1e-6f);
        assertEquals(expected.z, actual.z, 1e-6f);
    }
}
