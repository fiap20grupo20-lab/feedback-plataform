package com.fiap.feedback.controller;

import com.fiap.feedback.dto.FeedbackRequest;
import com.fiap.feedback.enums.Urgencia;
import com.fiap.feedback.model.Feedback;
import com.fiap.feedback.service.FeedbackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FeedbackControllerTest {

    @Mock
    private FeedbackService service;

    @InjectMocks
    private FeedbackController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new FeedbackExceptionHandler())
                .build();
    }

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
    void deveInformarTiposPermitidosQuandoUrgenciaForInvalidaNoCadastro() throws Exception {
        mockMvc.perform(post("/avaliacao")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "descricao": "Atendimento",
                                  "nota": 8,
                                  "urgencia": "CRITICA"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {
                          "error": "Urgência inválida. Tipos permitidos: BAIXA, MEDIA, ALTA."
                        }
                        """));
    }
}
