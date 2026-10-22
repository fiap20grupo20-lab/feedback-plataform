package com.fiap.feedback.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.text.Normalizer;
import java.util.Locale;

public enum Urgencia {
    BAIXA,
    MEDIA,
    ALTA;

    @JsonCreator
    public static Urgencia fromJson(String value) {
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT);
        return Urgencia.valueOf(normalized);
    }
}