package br.com.barberini.repository;

import br.com.barberini.model.Barbearia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BarbeariaRepository extends JpaRepository<Barbearia, Long> {
    Optional<Barbearia> findBySlugIgnoreCase(String slug);
    boolean existsBySlugIgnoreCase(String slug);
    Optional<Barbearia> findByMercadoPagoPreapprovalId(String mercadoPagoPreapprovalId);
}
