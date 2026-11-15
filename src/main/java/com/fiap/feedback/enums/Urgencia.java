package com.fiap.feedback.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public enum Urgencia {
    BAIXA,
    MEDIA,
    ALTA;

    @JsonCreator
    public static Urgencia fromJson(String value) {
        if (value == null) {
            throw invalidUrgencia();
        }

        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT);
        try {
            return Urgencia.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw invalidUrgencia();
        }
    }

    private static IllegalArgumentException invalidUrgencia() {
        String tiposPermitidos = Arrays.stream(values())
                .map(Urgencia::name)
                .collect(Collectors.joining(", "));
        return new IllegalArgumentException("Urgência inválida. Tipos permitidos: " + tiposPermitidos + ".");
    }
}