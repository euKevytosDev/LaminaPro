package br.com.barberini.dto;

import br.com.barberini.model.FormaPagamento;
import br.com.barberini.model.StatusAgendamento;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AtualizarStatusRequest(
        @NotNull StatusAgendamento status,
        BigDecimal valorCobrado,
        FormaPagamento formaPagamento
) {}
