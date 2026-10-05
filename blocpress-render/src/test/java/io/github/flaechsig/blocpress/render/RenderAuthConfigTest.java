package io.github.flaechsig.blocpress.render;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderAuthConfigTest {

    @Test
    @DisplayName("REQ-0009: enabledWithoutKeyRefusesToStart")
    void enabledWithoutKeyRefusesToStart() {
        var e = assertThrows(IllegalStateException.class,
                () -> RenderAuthConfig.validate(true, Optional.empty(), Optional.empty()));
        assertTrue(e.getMessage().contains("MP_JWT_VERIFY_PUBLICKEY"), e.getMessage());
        assertThrows(IllegalStateException.class,
                () -> RenderAuthConfig.validate(true, Optional.of(" "), Optional.empty()));
    }

    @Test
    @DisplayName("REQ-0009: enabledWithKeyOrLocationStarts")
    void enabledWithKeyOrLocationStarts() {
        assertDoesNotThrow(() -> RenderAuthConfig.validate(true, Optional.of("MIIB..."), Optional.empty()));
        assertDoesNotThrow(() -> RenderAuthConfig.validate(true, Optional.empty(), Optional.of("file:/keys/pub.pem")));
    }

    @Test
    void disabledNeedsNoKey() {
        assertDoesNotThrow(() -> RenderAuthConfig.validate(false, Optional.empty(), Optional.empty()));
    }
}
