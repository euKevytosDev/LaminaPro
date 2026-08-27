package br.com.barberini.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AtualizarLojaRequest(
        @NotBlank @Size(max = 120) String nome,
        @Size(max = 30) String telefone,
        String logoData
) {}
