package br.com.barberini.repository;

import br.com.barberini.model.Servico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServicoRepository extends JpaRepository<Servico, Long> {
    List<Servico> findByBarbeariaIdAndAtivoTrueOrderByNomeAsc(Long barbeariaId);
    List<Servico> findByBarbeariaIdOrderByNomeAsc(Long barbeariaId);
    Optional<Servico> findByIdAndBarbeariaId(Long id, Long barbeariaId);
    long countByBarbeariaId(Long barbeariaId);
}
