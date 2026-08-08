package cn.academy.client.render;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ACLegacyFontTest {
    @Test
    void selectedJavaFontMustCoverTheEntireMixedChineseString() {
        String text = "學園都市 AcademyCraft：超能力開發終端";
        Font selected = ACLegacyFont.selectedFont(text, false);

        assertTrue(ACLegacyFont.canRasterize(text, false));
        assertTrue(selected.canDisplayUpTo(text) < 0);
        assertTrue(ACLegacyFont.selectedFont(text, true).canDisplayUpTo(text) < 0);
    }

    @Test
    void traditionalChineseTranslationIsCompleteAndValid() throws IOException {
        JsonObject english = lang("en_us");
        JsonObject traditionalChinese = lang("zh_tw");

        assertEquals(english.keySet(), traditionalChinese.keySet());
        assertTrue(traditionalChinese.size() >= 600);
        for (var entry : traditionalChinese.entrySet()) {
            String value = entry.getValue().getAsString();
            assertFalse(value.contains("\uFFFD"), entry.getKey());
            assertFalse(value.isBlank(), entry.getKey());
        }
    }

    private static JsonObject lang(String locale) throws IOException {
        String path = "/assets/academy/lang/" + locale + ".json";
        try (InputStream stream = ACLegacyFontTest.class.getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing language resource " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
