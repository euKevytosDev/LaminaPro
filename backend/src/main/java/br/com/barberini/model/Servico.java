package br.com.barberini.model;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;

@Entity
@Table(name = "servicos")
public class Servico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "barbearia_id")
    private Barbearia barbearia;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal preco;

    @Column(nullable = false)
    private int duracaoMin = 30;

    @ColumnDefault("true")
    @Column(nullable = false)
    private boolean ativo = true;

    public Servico() {}

    public Servico(Barbearia barbearia, String nome, BigDecimal preco, int duracaoMin) {
        this.barbearia = barbearia;
        this.nome = nome;
        this.preco = preco;
        this.duracaoMin = duracaoMin;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Barbearia getBarbearia() { return barbearia; }
    public void setBarbearia(Barbearia barbearia) { this.barbearia = barbearia; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public BigDecimal getPreco() { return preco; }
    public void setPreco(BigDecimal preco) { this.preco = preco; }
    public int getDuracaoMin() { return duracaoMin; }
    public void setDuracaoMin(int duracaoMin) { this.duracaoMin = duracaoMin; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
}
