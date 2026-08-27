package br.com.barberini.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalTime;

/** Horário desativado pelo dono. Se barbeiro for null, aplica a todos da loja. */
@Entity
@Table(name = "bloqueios_horario")
public class BloqueioHorario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "barbearia_id")
    private Barbearia barbearia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "barbeiro_id")
    private Barbeiro barbeiro;

    @Column(nullable = false)
    private LocalDate data;

    @Column(nullable = false)
    private LocalTime hora;

    @Column(length = 160)
    private String motivo;

    public BloqueioHorario() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Barbearia getBarbearia() { return barbearia; }
    public void setBarbearia(Barbearia barbearia) { this.barbearia = barbearia; }
    public Barbeiro getBarbeiro() { return barbeiro; }
    public void setBarbeiro(Barbeiro barbeiro) { this.barbeiro = barbeiro; }
    public LocalDate getData() { return data; }
    public void setData(LocalDate data) { this.data = data; }
    public LocalTime getHora() { return hora; }
    public void setHora(LocalTime hora) { this.hora = hora; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
}
