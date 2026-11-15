package com.fiap.feedback.enums;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrgenciaTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deveDesserializarUrgenciaSemDiferenciarMaiusculas() throws Exception {
        assertEquals(Urgencia.ALTA, objectMapper.readValue("\"Alta\"", Urgencia.class));
        assertEquals(Urgencia.BAIXA, objectMapper.readValue("\"baixa\"", Urgencia.class));
    }

    @Test
    void deveAceitarMediaComAcento() throws Exception {
        assertEquals(Urgencia.MEDIA, objectMapper.readValue("\"Média\"", Urgencia.class));
    }

    @Test
    void deveInformarTiposPermitidosQuandoUrgenciaForInvalida() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> Urgencia.fromJson("CRITICA")
        );

        assertTrue(exception.getMessage().contains("BAIXA, MEDIA, ALTA"));
    }

    @Test
    void devePreservarMensagemDeUrgenciaInvalidaNaDesserializacao() {
        JsonProcessingException exception = assertThrows(
                JsonProcessingException.class,
                () -> objectMapper.readValue("\"CRITICA\"", Urgencia.class)
        );

        Throwable cause = exception;
        while (cause != null && !(cause instanceof IllegalArgumentException)) {
            cause = cause.getCause();
        }

        assertTrue(cause != null);
        assertTrue(cause.getMessage().contains("BAIXA, MEDIA, ALTA"));
    }
}
