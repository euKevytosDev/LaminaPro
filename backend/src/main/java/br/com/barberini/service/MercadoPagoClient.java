package br.com.barberini.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Cliente HTTP Mercado Pago — mesmo padrão do Pelada Oficial. */
@Component
public class MercadoPagoClient {

    private final RestClient http;
    private final String accessToken;

    public MercadoPagoClient(@Value("${app.mercadopago.access-token:}") String accessToken) {
        this.accessToken = accessToken == null ? "" : accessToken.trim();
        this.http = RestClient.builder().baseUrl("https://api.mercadopago.com").build();
    }

    public boolean configurado() {
        return !accessToken.isBlank();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> criarPreferencia(String titulo,
                                                BigDecimal valor,
                                                String externalRef,
                                                String notificationUrl,
                                                String successUrl,
                                                String failUrl) {
        exigirToken();

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("title", titulo);
        item.put("quantity", 1);
        item.put("currency_id", "BRL");
        item.put("unit_price", valor);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(item));
        body.put("external_reference", externalRef);
        if (notificationUrl != null && !notificationUrl.isBlank()) {
            body.put("notification_url", notificationUrl);
        }
        body.put("auto_return", "approved");
        body.put("back_urls", Map.of(
                "success", successUrl,
                "failure", failUrl,
                "pending", failUrl
        ));

        Map<String, Object> resp = http.post()
                .uri("/checkout/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(body)
                .retrieve()
                .body(Map.class);
        if (resp == null || resp.get("init_point") == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Mercado Pago não devolveu o checkout");
        }
        return resp;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> criarAssinaturaRecorrente(String titulo,
                                                         BigDecimal valor,
                                                         String externalRef,
                                                         String payerEmail,
                                                         String backUrl,
                                                         String notificationUrl) {
        exigirToken();

        Map<String, Object> autoRecurring = new LinkedHashMap<>();
        autoRecurring.put("frequency", 1);
        autoRecurring.put("frequency_type", "months");
        autoRecurring.put("transaction_amount", valor);
        autoRecurring.put("currency_id", "BRL");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", titulo);
        body.put("external_reference", externalRef);
        body.put("payer_email", payerEmail);
        body.put("auto_recurring", autoRecurring);
        body.put("back_url", backUrl);
        body.put("status", "pending");
        if (notificationUrl != null && !notificationUrl.isBlank()) {
            body.put("notification_url", notificationUrl);
        }

        Map<String, Object> resp = http.post()
                .uri("/preapproval")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(body)
                .retrieve()
                .body(Map.class);
        if (resp == null || (resp.get("init_point") == null && resp.get("sandbox_init_point") == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Mercado Pago não devolveu o checkout da assinatura");
        }
        return resp;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> buscarPagamento(String paymentId) {
        exigirToken();
        return http.get()
                .uri("/v1/payments/{id}", paymentId)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> buscarPreapproval(String preapprovalId) {
        exigirToken();
        return http.get()
                .uri("/preapproval/{id}", preapprovalId)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(Map.class);
    }

    private void exigirToken() {
        if (!configurado()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Pagamento ainda não está ligado. Configure MERCADOPAGO_ACCESS_TOKEN.");
        }
    }
}
