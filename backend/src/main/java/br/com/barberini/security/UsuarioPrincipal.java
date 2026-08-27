package br.com.barberini.security;

public class UsuarioPrincipal {
    private final Long id;
    private final String email;
    private final String papel;
    private final Long barbeariaId;

    public UsuarioPrincipal(Long id, String email, String papel, Long barbeariaId) {
        this.id = id;
        this.email = email;
        this.papel = papel;
        this.barbeariaId = barbeariaId;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPapel() { return papel; }
    public Long getBarbeariaId() { return barbeariaId; }

    public boolean isDono() {
        return "DONO".equalsIgnoreCase(papel);
    }
}
