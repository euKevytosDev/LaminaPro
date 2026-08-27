package br.com.barberini.security;

import br.com.barberini.model.Barbearia;
import br.com.barberini.repository.BarbeariaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class TenantSupport {

    private final BarbeariaRepository barbearias;

    public TenantSupport(BarbeariaRepository barbearias) {
        this.barbearias = barbearias;
    }

    /** Retorna a loja do dono autenticado ou 403. */
    public Barbearia exigirDonoComLoja() {
        AuthSupport.exigirDono();
        Long id = barbeariaIdAtual();
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Conta sem loja vinculada");
        }
        return barbearias.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Loja não encontrada"));
    }

    /** ID da barbearia no JWT/principal, ou null. */
    public Long barbeariaIdAtual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioPrincipal p)) {
            return null;
        }
        return p.getBarbeariaId();
    }
}
