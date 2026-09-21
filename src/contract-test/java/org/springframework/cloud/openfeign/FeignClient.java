package org.springframework.cloud.openfeign;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Contract-test classpath stub of OpenFeign's {@code @FeignClient}.
 *
 * <p>JustTest discovers this annotation by name and must compile without a hard
 * OpenFeign dependency. This type is not published.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FeignClient {

    String value() default "";

    String name() default "";

    String contextId() default "";
}
