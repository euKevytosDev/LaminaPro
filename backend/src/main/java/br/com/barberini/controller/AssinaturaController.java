package br.com.barberini.controller;

import br.com.barberini.model.Barbearia;
import br.com.barberini.security.AuthSupport;
import br.com.barberini.security.TenantSupport;
import br.com.barberini.service.MercadoPagoService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class AssinaturaController {

    private final MercadoPagoService mercadoPago;
    private final TenantSupport tenant;

    public AssinaturaController(MercadoPagoService mercadoPago, TenantSupport tenant) {
        this.mercadoPago = mercadoPago;
        this.tenant = tenant;
    }

    @PostMapping("/api/dono/assinatura/checkout")
    public Map<String, Object> checkout() {
        Barbearia loja = tenant.exigirDonoComLoja();
        String email = AuthSupport.atual().getEmail();
        return mercadoPago.criarCheckout(loja, email);
    }

    @PostMapping("/api/assinatura/webhook")
    public Map<String, String> webhook(@RequestBody(required = false) Map<String, Object> payload) {
        mercadoPago.processarWebhook(payload != null ? payload : Map.of());
        return Map.of("status", "ok");
    }
}
