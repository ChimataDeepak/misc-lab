package com.deepak.misclab.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Jakarta Bean Validation custom constraint: the value must be an upper-case ISO-4217 code known to the JDK
 * (e.g. USD, INR). Null is valid by convention; combine with @NotBlank when required.
 */
@Documented
@Constraint(validatedBy = IsoCurrencyValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface IsoCurrency {
    String message() default "must be an ISO-4217 currency code like USD or INR";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
