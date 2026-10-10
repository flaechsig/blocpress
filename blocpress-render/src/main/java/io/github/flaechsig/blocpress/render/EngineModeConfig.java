package io.github.flaechsig.blocpress.render;

import io.smallrye.config.ConfigSourceInterceptor;
import io.smallrye.config.ConfigSourceInterceptorContext;
import io.smallrye.config.ConfigValue;
import io.smallrye.config.Priorities;
import jakarta.annotation.Priority;

import java.util.Set;

/**
 * Betriebsart von render (ADR-0016): {@code BLOCPRESS_MODE=engine|full}, Standard {@code full}.
 *
 * <p>Im Engine-Modus braucht render keine Datenbank (REQ-0068): Dieser Interceptor setzt zur
 * Laufzeit Datenquelle, Hibernate ORM, Scheduler und den Datenbank-Check der Readiness auf
 * inaktiv (REQ-0070). So bleibt es ein
 * Artefakt und ein Image; der Modus wird beim Start gewaehlt. Ein ausdruecklich gesetzter
 * Wert der betroffenen Schluessel wird im Engine-Modus ueberstimmt.</p>
 *
 * <p>Bewusst nicht {@code blocpress.mode}: Das ist in blocpress-core ein System-Property fuer die
 * Aufloesung von Bausteinen ({@code file}/{@code server}).</p>
 */
@Priority(Priorities.APPLICATION)
public class EngineModeConfig implements ConfigSourceInterceptor {

    static final String MODE = "blocpress.render.mode";
    static final String ENGINE = "engine";

    /** Steht in application.properties, damit die Map der Health-Checks den Schluessel kennt. */
    static final String DATABASE_HEALTH_CHECK =
            "quarkus.smallrye-health.check.\"io.quarkus.agroal.runtime.health.DataSourceHealthCheck\".enabled";

    private static final Set<String> OFF_IN_ENGINE = Set.of(
            "quarkus.datasource.active",
            "quarkus.hibernate-orm.active",
            "quarkus.scheduler.enabled",
            DATABASE_HEALTH_CHECK);

    @Override
    public ConfigValue getValue(ConfigSourceInterceptorContext context, String name) {
        ConfigValue value = context.proceed(name);
        if (!OFF_IN_ENGINE.contains(name)) {
            return value;
        }
        ConfigValue mode = context.proceed(MODE);
        if (mode == null || !ENGINE.equalsIgnoreCase(mode.getValue())) {
            return value;
        }
        return (value != null ? value : ConfigValue.builder().withName(name).build())
                .withValue("false")
                .withConfigSourceName("blocpress engine mode");
    }
}
