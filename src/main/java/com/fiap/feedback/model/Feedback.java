package com.fiap.feedback.model;

import com.fiap.feedback.enums.Urgencia;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Feedback {
    @JsonProperty("id")
    private String id;

    @JsonProperty("descricao")
    private String descricao;

    @JsonProperty("nota")
    private Integer nota;

    @JsonProperty("urgencia")
    private Urgencia urgencia;

    @JsonProperty("dataEnvio")
    private String dataEnvio;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public Integer getNota() { return nota; }
    public void setNota(Integer nota) { this.nota = nota; }

    public Urgencia getUrgencia() { return urgencia; }
    public void setUrgencia(Urgencia urgencia) { this.urgencia = urgencia; }

    public String getDataEnvio() { return dataEnvio; }
    public void setDataEnvio(String dataEnvio) { this.dataEnvio = dataEnvio; }
}