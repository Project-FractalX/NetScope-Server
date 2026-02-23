package com.netscope.annotation;

import java.lang.annotation.*;

/**
 * Marks a method or field as secured — requires authentication.
 * The {@code auth} parameter is REQUIRED — you must explicitly choose which auth type.
 *
 * <p>On a METHOD:
 * <pre>{@code
 * @NetworkSecured(auth = AuthType.OAUTH)
 * public Customer getCustomer(String id) { ... }
 *
 * @NetworkSecured(auth = AuthType.API_KEY)
 * public void deleteCustomer(String id) { ... }
 *
 * @NetworkSecured(auth = AuthType.BOTH)
 * public List<Customer> listCustomers() { ... }
 * }</pre>
 *
 * <p>On a FIELD (returns the field value directly):
 * <pre>{@code
 * @NetworkSecured(auth = AuthType.OAUTH)
 * private String secretToken = "abc123";
 *
 * @NetworkSecured(auth = AuthType.API_KEY)
 * private int internalCounter = 42;
 * }</pre>
 */
@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NetworkSecured {

    /**
     * REQUIRED — which authentication type is accepted.
     *
     *   AuthType.OAUTH   — only OAuth 2.0 JWT token accepted
     *   AuthType.API_KEY — only API key accepted
     *   AuthType.BOTH    — either OAuth or API key accepted
     */
    AuthType auth();

    /**
     * Description for documentation.
     */
    String description() default "";
}
