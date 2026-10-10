package io.github.flaechsig.blocpress.render;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

/** render ist erst bereit, wenn alle LibreOffice-Instanzen laufen (REQ-0098). */
@Readiness
@ApplicationScoped
public class LibreOfficeReadiness implements HealthCheck {

    @Inject
    LibreOfficePool pool;

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.named("LibreOffice instances")
                .status(pool.ready())
                .withData("instances", pool.instances().size())
                .build();
    }
}
