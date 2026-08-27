package br.com.barberini.model;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "barbeiros")
public class Barbeiro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "barbearia_id")
    private Barbearia barbearia;

    @Column(nullable = false, length = 80)
    private String nome;

    @Column(nullable = false, length = 4)
    private String iniciais;

    @Column(nullable = false, length = 20)
    private String cor = "#3d3d3d";

    /** Foto comprimida (data URL) — leve, sem storage externo */
    @Column(length = 700000)
    private String fotoData;

    @ColumnDefault("true")
    @Column(nullable = false)
    private boolean ativo = true;

    public Barbeiro() {}

    public Barbeiro(Barbearia barbearia, String nome, String iniciais, String cor) {
        this.barbearia = barbearia;
        this.nome = nome;
        this.iniciais = iniciais;
        this.cor = cor;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Barbearia getBarbearia() { return barbearia; }
    public void setBarbearia(Barbearia barbearia) { this.barbearia = barbearia; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getIniciais() { return iniciais; }
    public void setIniciais(String iniciais) { this.iniciais = iniciais; }
    public String getCor() { return cor; }
    public void setCor(String cor) { this.cor = cor; }
    public String getFotoData() { return fotoData; }
    public void setFotoData(String fotoData) { this.fotoData = fotoData; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
}
