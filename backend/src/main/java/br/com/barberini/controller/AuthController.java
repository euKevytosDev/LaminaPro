package br.com.barberini.controller;

import br.com.barberini.dto.CadastroRequest;
import br.com.barberini.dto.CriarLojaRequest;
import br.com.barberini.dto.GoogleLoginRequest;
import br.com.barberini.dto.LoginRequest;
import br.com.barberini.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/cadastro")
    public Map<String, Object> cadastrar(@Valid @RequestBody CadastroRequest request) {
        return authService.cadastrar(request);
    }

    @PostMapping("/criar-loja")
    public Map<String, Object> criarLoja(@Valid @RequestBody CriarLojaRequest request) {
        return authService.cadastrarDono(request);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/google")
    public Map<String, Object> loginGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return authService.loginGoogle(request.credential());
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        return authService.usuarioAtual();
    }
}
