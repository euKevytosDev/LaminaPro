package br.com.barberini.controller;

import br.com.barberini.dto.CheckoutRequest;
import br.com.barberini.model.Barbearia;
import br.com.barberini.security.AuthSupport;
import br.com.barberini.security.TenantSupport;
import br.com.barberini.service.AssinaturaService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class AssinaturaController {

    private final AssinaturaService assinatura;
    private final TenantSupport tenant;

    public AssinaturaController(AssinaturaService assinatura, TenantSupport tenant) {
        this.assinatura = assinatura;
        this.tenant = tenant;
    }

    @GetMapping("/api/dono/assinatura/catalogo")
    public List<Map<String, Object>> catalogo() {
        tenant.exigirDonoComLoja();
        return assinatura.catalogo();
    }

    @GetMapping("/api/dono/assinatura/status")
    public Map<String, Object> status() {
        Barbearia loja = tenant.exigirDonoComLoja();
        return assinatura.statusLoja(loja);
    }

    @PostMapping("/api/dono/assinatura/checkout")
    public Map<String, Object> checkout(@RequestBody(required = false) CheckoutRequest request) {
        Barbearia loja = tenant.exigirDonoComLoja();
        String email = AuthSupport.atual().getEmail();
        String planoId = request != null ? request.planoId() : null;
        return assinatura.criarCheckout(loja, email, planoId);
    }

    @RequestMapping(value = "/api/assinatura/webhook", method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, String> webhook(
            @RequestParam(value = "data.id", required = false) String dataId,
            @RequestParam(value = "id", required = false) String id,
            @RequestBody(required = false) Map<String, Object> body) {
        String paymentId = dataId != null ? dataId : id;
        if (paymentId == null && body != null) {
            Object data = body.get("data");
            if (data instanceof Map<?, ?> mapa && mapa.get("id") != null) {
                paymentId = String.valueOf(mapa.get("id"));
            }
        }
        if (paymentId != null) {
            try {
                String type = body != null ? String.valueOf(body.getOrDefault("type", "")) : "";
                String action = body != null ? String.valueOf(body.getOrDefault("action", "")) : "";
                if (type.contains("subscription") || type.contains("preapproval")
                        || action.contains("subscription") || action.contains("preapproval")) {
                    assinatura.processarAssinatura(paymentId);
                } else {
                    assinatura.processarNotificacaoPagamento(paymentId);
                }
            } catch (Exception ignored) {
                /* MP reenvia; não devolver 500 */
            }
        }
        return Map.of("status", "ok");
    }
}
