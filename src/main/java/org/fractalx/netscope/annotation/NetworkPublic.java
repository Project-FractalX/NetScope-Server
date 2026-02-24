package org.fractalx.netscope.annotation;

import java.lang.annotation.*;

/**
 * Marks a method or field as publicly accessible — no authentication required.
 *
 * <p>On a METHOD:
 * <pre>{@code
 * @NetworkPublic
 * public List<String> getPublicInfo() { ... }
 * }</pre>
 *
 * <p>On a FIELD:
 * <pre>{@code
 * @NetworkPublic
 * private String appVersion = "1.0.0";   // returns "1.0.0" directly
 * }</pre>
 */
@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NetworkPublic {
    String description() default "";
}
