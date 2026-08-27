package br.com.barberini.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CriarLojaRequest(
        @NotBlank @Size(max = 80) String nome,
        @NotBlank @Email @Size(max = 120) String email,
        @NotBlank @Size(min = 6, max = 80) String senha,
        @NotBlank @Size(max = 120) String nomeBarbearia,
        @Size(max = 80) String slug,
        @Size(max = 30) String telefone
) {}
