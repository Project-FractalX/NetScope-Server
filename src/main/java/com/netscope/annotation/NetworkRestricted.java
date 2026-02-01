package com.netscope.annotation;

import org.springframework.web.bind.annotation.RequestMethod;
import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NetworkRestricted {
    // HTTP method (optional)
    RequestMethod method() default RequestMethod.GET;

    // Optional API key per method
    String key() default "";
}
