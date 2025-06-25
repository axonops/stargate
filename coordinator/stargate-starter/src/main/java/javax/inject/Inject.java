/*
 * Compatibility shim for javax.inject.Inject
 * This is a minimal implementation to support dependency injection
 */
package javax.inject;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Inject annotation compatibility shim. This annotation identifies injectable constructors,
 * methods, and fields.
 */
@Target({METHOD, CONSTRUCTOR, FIELD})
@Retention(RUNTIME)
@Documented
public @interface Inject {}
