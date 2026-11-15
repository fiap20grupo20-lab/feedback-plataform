package com.fiap.feedback.controller;

import com.fiap.feedback.dto.FeedbackRequest;
import com.fiap.feedback.enums.Urgencia;
import com.fiap.feedback.model.Feedback;
import com.fiap.feedback.service.FeedbackService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedbackControllerTest {

    @Mock
    private FeedbackService service;

    @InjectMocks
    private FeedbackController controller;

    @Test
    void deveCriarFeedbackERetornarStatusCreated() {
        FeedbackRequest request = new FeedbackRequest(
                "Atendimento foi ótimo",
                9,
                Urgencia.ALTA
        );

        Feedback feedback = new Feedback();
        feedback.setId("id-123");
        feedback.setDescricao(request.descricao());
        feedback.setNota(request.nota());
        feedback.setUrgencia(request.urgencia());
        feedback.setDataEnvio("2026-10-17T10:00:00");

        when(service.criar(request)).thenReturn(feedback);

        ResponseEntity<Feedback> resultado = controller.criar(request);

        assertNotNull(resultado);
        assertEquals(HttpStatus.CREATED, resultado.getStatusCode());
        assertNotNull(resultado.getBody());
        assertEquals("id-123", resultado.getBody().getId());
        assertEquals("Atendimento foi ótimo", resultado.getBody().getDescricao());
        assertEquals(9, resultado.getBody().getNota());
        assertEquals(Urgencia.ALTA, resultado.getBody().getUrgencia());

        verify(service, times(1)).criar(request);
    }

    @Test
                                }
                        }
    }
}
