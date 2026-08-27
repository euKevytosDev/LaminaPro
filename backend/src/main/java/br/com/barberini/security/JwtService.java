package br.com.barberini.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtService {

    private final SecretKey key;
    private final long expiracaoMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expiracaoMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiracaoMs = expiracaoMs;
    }

    public String gerarToken(Long usuarioId, String email, String papel, Long barbeariaId) {
        Date agora = new Date();
        var builder = Jwts.builder()
                .subject(String.valueOf(usuarioId))
                .claim("email", email)
                .claim("papel", papel)
                .issuedAt(agora)
                .expiration(new Date(agora.getTime() + expiracaoMs));
        if (barbeariaId != null) {
            builder.claim("barbeariaId", barbeariaId);
        }
        return builder.signWith(key).compact();
    }

    public Long extrairUsuarioId(String token) {
        return Long.valueOf(parse(token).getSubject());
    }

    public String extrairEmail(String token) {
        Object email = parse(token).get("email");
        return email != null ? String.valueOf(email) : null;
    }

    public String extrairPapel(String token) {
        Object papel = parse(token).get("papel");
        return papel != null ? String.valueOf(papel) : "CLIENTE";
    }

    public Long extrairBarbeariaId(String token) {
        Object v = parse(token).get("barbeariaId");
        if (v == null) return null;
        return Long.valueOf(String.valueOf(v));
    }

    public boolean valido(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
