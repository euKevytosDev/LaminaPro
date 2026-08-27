package br.com.barberini.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String email;

    @Column(nullable = false, length = 80)
    private String nome;

    @Column(nullable = false, length = 200)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private Papel papel = Papel.CLIENTE;

    /** Loja do dono. Clientes ficam null (podem agendar em várias lojas). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "barbearia_id")
    private Barbearia barbearia;

    @Column(length = 30)
    private String telefone;

    @Column(nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    public Usuario() {}

    public Usuario(String email, String nome, String senhaHash, Papel papel) {
        this.email = email;
        this.nome = nome;
        this.senhaHash = senhaHash;
        this.papel = papel;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getSenhaHash() { return senhaHash; }
    public void setSenhaHash(String senhaHash) { this.senhaHash = senhaHash; }
    public Papel getPapel() { return papel; }
    public void setPapel(Papel papel) { this.papel = papel; }
    public Barbearia getBarbearia() { return barbearia; }
    public void setBarbearia(Barbearia barbearia) { this.barbearia = barbearia; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }
}
