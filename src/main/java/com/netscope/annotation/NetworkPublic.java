package com.netscope.annotation;

import java.lang.annotation.*;

/**
 * Marks a method or field as publicly accessible — no authentication required.
 *
 * On a METHOD:
 *   @NetworkPublic
 *   public List<String> getPublicInfo() { ... }
 *
 * On a FIELD:
 *   @NetworkPublic
 *   private String appVersion = "2.0.1";   // returns "2.0.1" directly
 */
@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NetworkPublic {
    String description() default "";
}
