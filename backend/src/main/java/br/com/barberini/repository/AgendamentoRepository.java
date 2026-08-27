package br.com.barberini.repository;

import br.com.barberini.model.Agendamento;
import br.com.barberini.model.StatusAgendamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AgendamentoRepository extends JpaRepository<Agendamento, Long> {

    @Query("""
            select a from Agendamento a
            join fetch a.cliente
            join fetch a.barbeiro
            join fetch a.servico
            where a.cliente.id = :clienteId and a.status <> :statusExcluido
            order by a.data asc, a.horaInicio asc
            """)
    List<Agendamento> findDoClienteExcluindoStatus(
            @Param("clienteId") Long clienteId,
            @Param("statusExcluido") StatusAgendamento statusExcluido);

    /** Ocupa a agenda: tudo que não foi cancelado (confirmado, finalizado ou no-show) */
    @Query("""
            select a from Agendamento a
            join fetch a.cliente
            join fetch a.barbeiro
            join fetch a.servico
            where a.barbeiro.id = :barbeiroId and a.data = :data and a.status <> :statusExcluido
            """)
    List<Agendamento> findOcupadosDoDia(
            @Param("barbeiroId") Long barbeiroId,
            @Param("data") LocalDate data,
            @Param("statusExcluido") StatusAgendamento statusExcluido);

    @Query("""
            select a from Agendamento a
            join fetch a.cliente
            join fetch a.barbeiro
            join fetch a.servico
            where a.barbearia.id = :barbeariaId
              and a.data between :inicio and :fim
              and a.status <> :statusExcluido
            order by a.data asc, a.horaInicio asc
            """)
    List<Agendamento> findByBarbeariaIdAndPeriodoExcluindoStatus(
            @Param("barbeariaId") Long barbeariaId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim,
            @Param("statusExcluido") StatusAgendamento statusExcluido);

    @Query("""
            select a from Agendamento a
            join fetch a.cliente
            join fetch a.barbeiro
            join fetch a.servico
            where a.barbearia.id = :barbeariaId and a.data between :inicio and :fim
            order by a.data asc, a.horaInicio asc
            """)
    List<Agendamento> findByBarbeariaIdAndPeriodo(
            @Param("barbeariaId") Long barbeariaId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim);

    /** Clientes que já tinham histórico antes do período — para separar novos de recorrentes */
    @Query("""
            select distinct a.cliente.id from Agendamento a
            where a.barbearia.id = :barbeariaId
              and a.cliente.id in :clienteIds
              and a.data < :inicio
              and a.status <> :statusExcluido
            """)
    List<Long> clientesComHistoricoAntesNaLoja(
            @Param("barbeariaId") Long barbeariaId,
            @Param("clienteIds") List<Long> clienteIds,
            @Param("inicio") LocalDate inicio,
            @Param("statusExcluido") StatusAgendamento statusExcluido);

    @Query("""
            select a from Agendamento a
            join fetch a.cliente
            join fetch a.barbeiro
            join fetch a.servico
            join fetch a.barbearia
            where a.id = :id
            """)
    Optional<Agendamento> findByIdComDetalhes(@Param("id") Long id);

    Optional<Agendamento> findByIdAndBarbeariaId(Long id, Long barbeariaId);
}
