package br.com.barberini.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record CriarAgendamentoRequest(
        @NotBlank @Size(max = 80) String slug,
        Long barbeiroId,
        @NotNull Long servicoId,
        @NotNull LocalDate data,
        @NotNull LocalTime horaInicio,
        @Size(max = 200) String observacao,
        Boolean semPreferencia
) {}
