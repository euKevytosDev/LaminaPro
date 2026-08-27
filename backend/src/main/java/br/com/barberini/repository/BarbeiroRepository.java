package br.com.barberini.repository;

import br.com.barberini.model.Barbeiro;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BarbeiroRepository extends JpaRepository<Barbeiro, Long> {
    List<Barbeiro> findByBarbeariaIdAndAtivoTrueOrderByNomeAsc(Long barbeariaId);
    List<Barbeiro> findByBarbeariaIdOrderByNomeAsc(Long barbeariaId);
    Optional<Barbeiro> findByIdAndBarbeariaId(Long id, Long barbeariaId);
    long countByBarbeariaId(Long barbeariaId);
}
