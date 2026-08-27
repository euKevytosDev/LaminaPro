package br.com.barberini.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BarbeiroRequest(
        @NotBlank @Size(max = 80) String nome,
        @Size(max = 4) String iniciais,
        @Size(max = 20) String cor,
        Boolean ativo,
        String fotoData
) {}
