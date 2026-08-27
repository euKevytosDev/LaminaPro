package br.com.barberini.model;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "agendamentos")
public class Agendamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "barbearia_id")
    private Barbearia barbearia;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Usuario cliente;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "barbeiro_id")
    private Barbeiro barbeiro;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "servico_id")
    private Servico servico;

    @Column(nullable = false)
    private LocalDate data;

    @Column(nullable = false)
    private LocalTime horaInicio;

    @Column(nullable = false)
    private LocalTime horaFim;

    @Column(length = 200)
    private String observacao;

    /** Preço congelado no momento do agendamento — histórico não muda se o serviço mudar de preço */
    @Column(precision = 10, scale = 2)
    private BigDecimal precoCobrado;

    // varchar explícito: evita ENUM/CHECK gerado pelo dialeto, que o ddl-auto nunca atualiza
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private StatusAgendamento status = StatusAgendamento.CONFIRMADO;

    /** true quando o cliente não escolheu profissional (encaixe automático, dono pode remanejar) */
    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean semPreferencia = false;

    @Column(nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    public Agendamento() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Barbearia getBarbearia() { return barbearia; }
    public void setBarbearia(Barbearia barbearia) { this.barbearia = barbearia; }
    public Usuario getCliente() { return cliente; }
    public void setCliente(Usuario cliente) { this.cliente = cliente; }
    public Barbeiro getBarbeiro() { return barbeiro; }
    public void setBarbeiro(Barbeiro barbeiro) { this.barbeiro = barbeiro; }
    public Servico getServico() { return servico; }
    public void setServico(Servico servico) { this.servico = servico; }
    public LocalDate getData() { return data; }
    public void setData(LocalDate data) { this.data = data; }
    public LocalTime getHoraInicio() { return horaInicio; }
    public void setHoraInicio(LocalTime horaInicio) { this.horaInicio = horaInicio; }
    public LocalTime getHoraFim() { return horaFim; }
    public void setHoraFim(LocalTime horaFim) { this.horaFim = horaFim; }
    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }
    public BigDecimal getPrecoCobrado() { return precoCobrado; }
    public void setPrecoCobrado(BigDecimal precoCobrado) { this.precoCobrado = precoCobrado; }
    public StatusAgendamento getStatus() { return status; }
    public void setStatus(StatusAgendamento status) { this.status = status; }
    public boolean isSemPreferencia() { return semPreferencia; }
    public void setSemPreferencia(boolean semPreferencia) { this.semPreferencia = semPreferencia; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }
}
