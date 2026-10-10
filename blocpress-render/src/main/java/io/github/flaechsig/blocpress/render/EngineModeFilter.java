package io.github.flaechsig.blocpress.render;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

/**
 * Im Engine-Modus gibt es nur das Rendern einer mitgeschickten Vorlage (ADR-0016). Alle anderen
 * Pfade der API — Rendern per Namen, Jobs, Dashboard, Import — brauchen die Datenbank und
 * antworten mit 404 und einer Meldung, die den Modus nennt, ohne Interna (REQ-0069).
 */
public class EngineModeFilter {

    static final String MESSAGE = "Not available in engine mode (BLOCPRESS_MODE=engine): "
            + "this render service only offers POST /api/render/template.";

    @ConfigProperty(name = EngineModeConfig.MODE, defaultValue = "full")
    String mode;

    @ServerRequestFilter(preMatching = true)
    public Response rejectDatabasePaths(ContainerRequestContext request) {
        if (!EngineModeConfig.ENGINE.equalsIgnoreCase(mode)) {
            return null;
        }
        String path = "/" + request.getUriInfo().getPath().replaceAll("^/+", "").replaceAll("/+$", "");
        if (!path.startsWith("/api/")) {
            return null;
        }
        if (path.equals("/api/render/template") && "POST".equals(request.getMethod())) {
            return null;
        }
        return Response.status(Response.Status.NOT_FOUND)
                .type(MediaType.TEXT_PLAIN_TYPE)
                .entity(MESSAGE)
                .build();
    }
}
