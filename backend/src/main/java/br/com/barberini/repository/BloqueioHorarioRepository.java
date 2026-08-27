package br.com.barberini.repository;

import br.com.barberini.model.BloqueioHorario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BloqueioHorarioRepository extends JpaRepository<BloqueioHorario, Long> {

    @Query("select b from BloqueioHorario b left join fetch b.barbeiro where b.barbearia.id = :barbeariaId and b.data = :data")
    List<BloqueioHorario> findByBarbeariaIdAndData(
            @Param("barbeariaId") Long barbeariaId, @Param("data") LocalDate data);

    @Query("select b from BloqueioHorario b left join fetch b.barbeiro where b.barbearia.id = :barbeariaId and b.data = :data and b.barbeiro.id = :barbeiroId")
    List<BloqueioHorario> findByBarbeariaIdAndDataAndBarbeiroId(
            @Param("barbeariaId") Long barbeariaId,
            @Param("data") LocalDate data,
            @Param("barbeiroId") Long barbeiroId);

    @Query("select b from BloqueioHorario b where b.barbearia.id = :barbeariaId and b.data = :data and b.barbeiro is null")
    List<BloqueioHorario> findByBarbeariaIdAndDataAndBarbeiroIsNull(
            @Param("barbeariaId") Long barbeariaId, @Param("data") LocalDate data);

    @Query("select b from BloqueioHorario b left join fetch b.barbeiro where b.barbearia.id = :barbeariaId and b.data between :inicio and :fim")
    List<BloqueioHorario> findByBarbeariaIdAndDataBetween(
            @Param("barbeariaId") Long barbeariaId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim);

    Optional<BloqueioHorario> findByIdAndBarbeariaId(Long id, Long barbeariaId);
}
