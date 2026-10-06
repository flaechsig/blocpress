package io.github.flaechsig.blocpress.studio;

import jakarta.ws.rs.HttpMethod;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** WebDAV-Methode UNLOCK (RFC 4918) für {@link WorkbenchApiProxy}. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@HttpMethod("UNLOCK")
public @interface UNLOCK {
}
