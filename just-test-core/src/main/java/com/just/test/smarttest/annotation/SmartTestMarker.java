package com.just.test.smarttest.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Internal marker shared by the Boot 2 and Boot 3 {@code @SmartTest} annotations.
 * Consumers should use {@code @SmartTest}; this marker only keeps the common runtime independent
 * from a concrete Spring Boot bootstrapper class.
 */
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SmartTestMarker {
}
