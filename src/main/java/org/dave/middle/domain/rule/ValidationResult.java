package org.dave.middle.domain.rule;

public record ValidationResult(boolean valid, String error) {

    private static final ValidationResult OK = new ValidationResult(true, null);

    public static ValidationResult ok() {
        return OK;
    }

    public static ValidationResult fail(String error) {
        if (error == null || error.isBlank()) {
            throw new IllegalArgumentException("Текст ошибки обязателен");
        }
        return new ValidationResult(false, error);
    }
}
