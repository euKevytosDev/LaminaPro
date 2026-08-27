package br.com.barberini.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record SyncBloqueiosRequest(
        @NotNull LocalDate data,
        Long barbeiroId,
        @Valid List<BloqueioItem> bloqueios,
        List<Long> removerIds
) {
    public record BloqueioItem(
            @NotNull LocalTime hora,
            @Size(max = 160) String motivo
    ) {}
}
