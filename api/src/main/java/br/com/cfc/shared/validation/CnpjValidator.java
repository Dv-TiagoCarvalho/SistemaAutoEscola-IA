package br.com.cfc.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CnpjValidator implements ConstraintValidator<Cnpj, String> {

    @Override
    public boolean isValid(String valor, ConstraintValidatorContext context) {
        return valor == null || valor.isBlank() || Documentos.cnpjValido(valor);
    }
}
