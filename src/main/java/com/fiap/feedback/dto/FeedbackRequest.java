package com.fiap.feedback.dto;

import com.fiap.feedback.enums.Urgencia;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FeedbackRequest(
        @NotBlank(message = "Descrição é obrigatória") String descricao,
        @NotNull(message = "Nota é obrigatória") @Min(value = 0, message = "Nota mínima é 0") @Max(value = 10, message = "Nota máxima é 10") Integer nota,
        @NotNull(message = "Urgência é obrigatória") Urgencia urgencia
) {
}