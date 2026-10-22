package com.fiap.feedback.controller;

import com.fiap.feedback.dto.FeedbackRequest;
import com.fiap.feedback.model.Feedback;
import com.fiap.feedback.service.FeedbackService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/avaliacao")
public class FeedbackController {
    private final FeedbackService service;

    public FeedbackController(FeedbackService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Feedback> criar(@Valid @RequestBody FeedbackRequest request) {
        Feedback feedback = service.criar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(feedback);
    }

    @GetMapping
    public ResponseEntity<List<Feedback>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}