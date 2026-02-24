package org.fractalx.netscope.server.annotation;

import org.fractalx.netscope.server.config.NetScopeAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * Enables the NetScope gRPC server in a Spring Boot application.
 *
 * <p>Add this annotation to your {@code @SpringBootApplication} class:
 * <pre>
 * {@literal @}SpringBootApplication
 * {@literal @}EnableNetScopeServer
 * public class MyApplication {
 *     public static void main(String[] args) {
 *         SpringApplication.run(MyApplication.class, args);
 *     }
 * }
 * </pre>
 *
 * <p>Spring Boot auto-configuration already activates NetScope automatically
 * when the library is on the classpath. Use this annotation as an explicit
 * alternative if auto-configuration is not triggering — for example, in
 * non-Boot Spring applications or when using custom application contexts.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(NetScopeAutoConfiguration.class)
public @interface EnableNetScopeServer {
}
