package com.fiap.feedback.enums;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
