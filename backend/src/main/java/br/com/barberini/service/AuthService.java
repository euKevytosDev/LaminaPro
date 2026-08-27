package br.com.barberini.service;

import br.com.barberini.dto.CadastroRequest;
import br.com.barberini.dto.CriarLojaRequest;
import br.com.barberini.dto.LoginRequest;
import br.com.barberini.model.Barbearia;
import br.com.barberini.model.Papel;
import br.com.barberini.model.Usuario;
import br.com.barberini.repository.BarbeariaRepository;
import br.com.barberini.repository.UsuarioRepository;
import br.com.barberini.security.JwtService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private final UsuarioRepository usuarios;
    private final BarbeariaRepository barbearias;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final GoogleTokenService googleTokens;

    public AuthService(
            UsuarioRepository usuarios,
            BarbeariaRepository barbearias,
            PasswordEncoder encoder,
            JwtService jwt,
            GoogleTokenService googleTokens
    ) {
        this.usuarios = usuarios;
        this.barbearias = barbearias;
        this.encoder = encoder;
        this.jwt = jwt;
        this.googleTokens = googleTokens;
    }

    @Transactional
    public Map<String, Object> cadastrar(CadastroRequest req) {
        String email = req.email().trim().toLowerCase();
        if (usuarios.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail já cadastrado");
        }
        Usuario u = usuarios.save(new Usuario(
                email, req.nome().trim(), encoder.encode(req.senha()), Papel.CLIENTE
        ));
        return respostaAuth(u);
    }

    @Transactional
    public Map<String, Object> cadastrarDono(CriarLojaRequest req) {
        String email = req.email().trim().toLowerCase();
        if (usuarios.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail já cadastrado");
        }

        String slug;
        if (req.slug() != null && !req.slug().isBlank()) {
            slug = slugify(req.slug());
            if (slug.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slug inválido");
            }
            if (barbearias.existsBySlugIgnoreCase(slug)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Este link da loja já está em uso");
            }
        } else {
            slug = resolverSlugUnico(req.nomeBarbearia());
        }

        Barbearia loja = new Barbearia(req.nomeBarbearia().trim(), slug);
        if (req.telefone() != null && !req.telefone().isBlank()) {
            loja.setTelefone(req.telefone().trim());
        }
        loja = barbearias.save(loja);

        Usuario u = new Usuario(
                email, req.nome().trim(), encoder.encode(req.senha()), Papel.DONO
        );
        u.setBarbearia(loja);
        if (req.telefone() != null && !req.telefone().isBlank()) {
            u.setTelefone(req.telefone().trim());
        }
        u = usuarios.save(u);
        return respostaAuth(u);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> login(LoginRequest req) {
        Usuario u = usuarios.findByEmailIgnoreCase(req.email().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos"));
        if (!encoder.matches(req.senha(), u.getSenhaHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos");
        }
        return respostaAuth(u);
    }

    @Transactional
    public Map<String, Object> loginGoogle(String credential) {
        GoogleIdToken.Payload payload = googleTokens.verificar(credential);
        String email = payload.getEmail().trim().toLowerCase();
        String nome = payload.get("name") != null
                ? String.valueOf(payload.get("name")).trim()
                : email.split("@")[0];
        if (nome.isBlank()) {
            nome = "Cliente";
        }

        Usuario u = usuarios.findByEmailIgnoreCase(email).orElse(null);
        if (u == null) {
            u = usuarios.save(new Usuario(
                    email,
                    nome,
                    encoder.encode(UUID.randomUUID().toString()),
                    Papel.CLIENTE
            ));
        }
        return respostaAuth(u);
    }

    private Map<String, Object> respostaAuth(Usuario u) {
        Long barbeariaId = u.getBarbearia() != null ? u.getBarbearia().getId() : null;
        String token = jwt.gerarToken(u.getId(), u.getEmail(), u.getPapel().name(), barbeariaId);

        Map<String, Object> usuario = new LinkedHashMap<>();
        usuario.put("id", u.getId());
        usuario.put("nome", u.getNome());
        usuario.put("email", u.getEmail());
        usuario.put("papel", u.getPapel().name());
        if (barbeariaId != null) {
            usuario.put("barbeariaId", barbeariaId);
            usuario.put("slug", u.getBarbearia().getSlug());
            usuario.put("nomeBarbearia", u.getBarbearia().getNome());
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("token", token);
        out.put("usuario", usuario);
        return out;
    }

    private String resolverSlugUnico(String nomeBarbearia) {
        String base = slugify(nomeBarbearia);
        if (base.isBlank()) {
            base = "loja";
        }
        String candidato = base;
        int i = 1;
        while (barbearias.existsBySlugIgnoreCase(candidato)) {
            candidato = base + "-" + (++i);
        }
        return candidato;
    }

    static String slugify(String texto) {
        if (texto == null) return "";
        String n = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return n.length() > 80 ? n.substring(0, 80).replaceAll("-+$", "") : n;
    }
}
