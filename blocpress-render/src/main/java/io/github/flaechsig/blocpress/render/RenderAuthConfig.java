package io.github.flaechsig.blocpress.render;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Optionale JWT-Absicherung der Render-API (ADR-002).
 *
 * <p>Die eigentliche Pruefung uebernimmt die HTTP-Berechtigung {@code render-api} aus
 * {@code application.properties}, deren {@code enabled}-Flag an
 * {@code blocpress.auth.enabled} ({@code BLOCPRESS_AUTH_ENABLED}) haengt. Diese Bean
 * stellt beim Start sicher, dass bei eingeschaltetem JWT ein Schluessel zur
 * Token-Pruefung konfiguriert ist — sonst bricht der Start ab (REQ-0009).</p>
 */
@Startup
@ApplicationScoped
public class RenderAuthConfig {

    private static final Logger LOG = LoggerFactory.getLogger(RenderAuthConfig.class);

    static final String ENABLED = "blocpress.auth.enabled";
    static final String PUBLIC_KEY = "mp.jwt.verify.publickey";
    static final String PUBLIC_KEY_LOCATION = "mp.jwt.verify.publickey.location";

    @ConfigProperty(name = ENABLED, defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = PUBLIC_KEY)
    Optional<String> publicKey;

    @ConfigProperty(name = PUBLIC_KEY_LOCATION)
    Optional<String> publicKeyLocation;

    @PostConstruct
    void init() {
        validate(enabled, publicKey, publicKeyLocation);
        LOG.info(enabled
                ? "JWT-Absicherung der Render-API: AKTIV (Bearer-Token erforderlich)"
                : "JWT-Absicherung der Render-API: aus (BLOCPRESS_AUTH_ENABLED=false)");
    }

    /**
     * @throws IllegalStateException wenn JWT eingeschaltet, aber kein Schluessel konfiguriert ist
     */
    static void validate(boolean enabled, Optional<String> publicKey, Optional<String> publicKeyLocation) {
        if (enabled && isBlank(publicKey) && isBlank(publicKeyLocation)) {
            throw new IllegalStateException(ENABLED + "=true, aber kein Schluessel zur Token-Pruefung "
                    + "konfiguriert: " + PUBLIC_KEY + " (MP_JWT_VERIFY_PUBLICKEY) oder " + PUBLIC_KEY_LOCATION
                    + " (MP_JWT_VERIFY_PUBLICKEY_LOCATION) setzen.");
        }
    }

    private static boolean isBlank(Optional<String> value) {
        return value.map(String::isBlank).orElse(true);
    }
}
