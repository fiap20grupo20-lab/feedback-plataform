package com.fiap.feedback.service;

import com.fiap.feedback.dto.FeedbackRequest;
import com.fiap.feedback.enums.Urgencia;
import com.fiap.feedback.model.Feedback;
import com.fiap.feedback.repository.FeedbackRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock
    private FeedbackRepository repository;

    @InjectMocks
    private FeedbackService service;

    @Test
    void deveSalvarFeedbackComDadosValidos() {
        FeedbackRequest request = new FeedbackRequest("Atendimento foi ótimo", 9, Urgencia.ALTA);
        Feedback feedbackPersistido = new Feedback();
        feedbackPersistido.setId("id-123");
        feedbackPersistido.setDescricao(request.descricao());
        feedbackPersistido.setNota(request.nota());
        feedbackPersistido.setUrgencia(request.urgencia());
        feedbackPersistido.setDataEnvio(java.time.LocalDateTime.now().toString());

        when(repository.save(any(Feedback.class))).thenReturn(feedbackPersistido);

        Feedback resultado = service.criar(request);

        assertNotNull(resultado);
        assertEquals("id-123", resultado.getId());
        assertEquals("Atendimento foi ótimo", resultado.getDescricao());
        assertEquals(9, resultado.getNota());
        assertEquals(Urgencia.ALTA, resultado.getUrgencia());
        assertNotNull(resultado.getDataEnvio());
        verify(repository, times(1)).save(any(Feedback.class));
    }

    @Test
    void deveListarFeedbacks() {
        Feedback feedback = new Feedback();
        feedback.setId("id-456");
        feedback.setDescricao("Precisa melhorar o tempo de resposta");
        feedback.setNota(6);
        feedback.setUrgencia(Urgencia.MEDIA);

        when(repository.findAll()).thenReturn(List.of(feedback));

        List<Feedback> resultado = service.listar();

        assertEquals(1, resultado.size());
        assertEquals("id-456", resultado.get(0).getId());
        assertEquals("Precisa melhorar o tempo de resposta", resultado.get(0).getDescricao());
        verify(repository, times(1)).findAll();
    }
}
