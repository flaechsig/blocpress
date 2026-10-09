package io.github.flaechsig.blocpress.render;

import io.github.flaechsig.blocpress.core.TextBlockRejectedException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Abgelehnte Bausteine ergeben 422 mit der kurzen Meldung der Ablehnung, ohne technische Details
 * (REQ-0034, REQ-0035).
 */
@Provider
public class TextBlockRejectedExceptionMapper implements ExceptionMapper<TextBlockRejectedException> {

    private static final Logger LOG = LoggerFactory.getLogger(TextBlockRejectedExceptionMapper.class);

    @Override
    public Response toResponse(TextBlockRejectedException e) {
        LOG.warn("Template rejected: {}", e.getMessage());
        return Response.status(422)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", e.getMessage()))
                .build();
    }
}
