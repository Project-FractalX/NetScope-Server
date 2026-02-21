package com.netscope.annotation;

import java.lang.annotation.*;

/**
 * Marks a method or field as secured — requires authentication.
 * The auth parameter is REQUIRED — you must explicitly choose which auth type.
 *
 * On a METHOD:
 *   @NetworkSecured(auth = AuthType.OAUTH)
 *   public Customer getCustomer(String id) { ... }
 *
 *   @NetworkSecured(auth = AuthType.API_KEY)
 *   public void deleteCustomer(String id) { ... }  // returns {"status":"accepted"}
 *
 *   @NetworkSecured(auth = AuthType.BOTH)
 *   public List<Customer> listCustomers() { ... }
 *
 * On a FIELD (returns the field value directly):
 *   @NetworkSecured(auth = AuthType.OAUTH)
 *   private String secretToken = "abc123";         // returns "abc123" directly
 *
 *   @NetworkSecured(auth = AuthType.API_KEY)
 *   private int internalCounter = 42;              // returns 42 directly
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
