package com.fiap.feedback.service;

import com.fiap.feedback.dto.FeedbackRequest;
import com.fiap.feedback.model.Feedback;
import com.fiap.feedback.repository.FeedbackRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class FeedbackService {
    private final FeedbackRepository repository;

    public FeedbackService(FeedbackRepository repository) {
        this.repository = repository;
    }

    public Feedback criar(FeedbackRequest req) {
        Feedback feedback = new Feedback();
        feedback.setId(UUID.randomUUID().toString());
        feedback.setDescricao(req.descricao());
        feedback.setNota(req.nota());
        feedback.setUrgencia(req.urgencia());
        feedback.setDataEnvio(LocalDateTime.now(ZoneOffset.UTC).toString());
        return repository.save(feedback);
    }

    public List<Feedback> listar() {
        return repository.findAll();
    }
}