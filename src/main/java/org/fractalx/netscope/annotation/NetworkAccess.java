package org.fractalx.netscope.annotation;

import java.lang.annotation.*;

/**
 * Meta-annotation that marks an annotation as providing network access.
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NetworkAccess {
}
