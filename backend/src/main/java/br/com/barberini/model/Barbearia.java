package br.com.barberini.model;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

@Entity
@Table(name = "barbearias", indexes = {
        @Index(name = "idx_barbearias_slug", columnList = "slug", unique = true)
})
public class Barbearia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    /** Link público: /#/loja/{slug} */
    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(length = 500000)
    private String logoData;

    @Column(length = 30)
    private String telefone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private PlanoBarbearia plano = PlanoBarbearia.TRIAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private StatusAssinatura statusAssinatura = StatusAssinatura.TRIAL;

    @Column(length = 120)
    private String mercadoPagoPreapprovalId;

    @ColumnDefault("true")
    @Column(nullable = false)
    private boolean ativo = true;

    @Column(nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    public Barbearia() {}

    public Barbearia(String nome, String slug) {
        this.nome = nome;
        this.slug = slug;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getLogoData() { return logoData; }
    public void setLogoData(String logoData) { this.logoData = logoData; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public PlanoBarbearia getPlano() { return plano; }
    public void setPlano(PlanoBarbearia plano) { this.plano = plano; }
    public StatusAssinatura getStatusAssinatura() { return statusAssinatura; }
    public void setStatusAssinatura(StatusAssinatura statusAssinatura) { this.statusAssinatura = statusAssinatura; }
    public String getMercadoPagoPreapprovalId() { return mercadoPagoPreapprovalId; }
    public void setMercadoPagoPreapprovalId(String mercadoPagoPreapprovalId) {
        this.mercadoPagoPreapprovalId = mercadoPagoPreapprovalId;
    }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }
}
