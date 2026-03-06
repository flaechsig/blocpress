package io.github.flaechsig.blocpress.workbench;

import jakarta.ws.rs.HttpMethod;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * JAX-RS custom HTTP method annotation for WebDAV PROPFIND.
 * JAX-RS spec §3.3 allows custom methods via @HttpMethod.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@HttpMethod("PROPFIND")
public @interface PROPFIND {
}
