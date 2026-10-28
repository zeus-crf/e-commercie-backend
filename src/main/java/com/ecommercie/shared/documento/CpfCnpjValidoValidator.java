package com.ecommercie.shared.documento;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CpfCnpjValidoValidator implements ConstraintValidator<CpfCnpjValido, String> {
    @Override
    public boolean isValid(String valor, ConstraintValidatorContext constraintValidatorContext) {
        return valor == null || CpfCnpj.valido(valor);
    }
}
