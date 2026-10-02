package io.github.flaechsig.blocpress.render;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderLocaleConfigTest {

    @Test
    void acceptsBcp47Tags() {
        assertEquals(Locale.GERMANY, RenderLocaleConfig.parseAndValidate("de-DE"));
        assertEquals(Locale.US, RenderLocaleConfig.parseAndValidate("en-US"));
        assertEquals(Locale.of("de", "CH"), RenderLocaleConfig.parseAndValidate(" de-CH "));
        // POSIX-Schreibweise wie in LANG wird toleriert
        assertEquals(Locale.of("de", "AT"), RenderLocaleConfig.parseAndValidate("de_AT"));
    }

    @Test
    void rejectsLocaleWithoutLanguageData() {
        var e = assertThrows(IllegalStateException.class, () -> RenderLocaleConfig.parseAndValidate("xx-XX"));
        assertTrue(e.getMessage().contains("keine Sprachdaten"), e.getMessage());
    }

    @Test
    void rejectsInvalidOrEmptyTag() {
        assertThrows(IllegalStateException.class, () -> RenderLocaleConfig.parseAndValidate(""));
        assertThrows(IllegalStateException.class, () -> RenderLocaleConfig.parseAndValidate("!!"));
    }

    @Test
    void factoryExposesValidatedLocale() {
        assertEquals(Locale.US, RenderLocaleConfig.of("en-US").defaultLocale());
    }
}
