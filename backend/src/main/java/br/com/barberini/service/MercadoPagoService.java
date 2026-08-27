package br.com.barberini.service;

import br.com.barberini.model.Barbearia;
import br.com.barberini.model.PlanoBarbearia;
import br.com.barberini.model.StatusAssinatura;
import br.com.barberini.repository.BarbeariaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class MercadoPagoService {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoService.class);
    private static final String PREAPPROVAL_URL = "https://api.mercadopago.com/preapproval";

    private final BarbeariaRepository barbearias;
    private final ObjectMapper mapper;
    private final String accessToken;
    private final String publicKey;
    private final BigDecimal precoPlano;
    private final String backUrl;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public MercadoPagoService(
            BarbeariaRepository barbearias,
            ObjectMapper mapper,
            @Value("${app.mercadopago.access-token:}") String accessToken,
            @Value("${app.mercadopago.public-key:}") String publicKey,
            @Value("${app.plano.preco:49.90}") BigDecimal precoPlano,
            @Value("${app.public-url:http://localhost:5173}") String publicUrl) {
        this.barbearias = barbearias;
        this.mapper = mapper;
        this.accessToken = accessToken != null ? accessToken.trim() : "";
        this.publicKey = publicKey != null ? publicKey.trim() : "";
        this.precoPlano = precoPlano;
        this.backUrl = publicUrl.endsWith("/") ? publicUrl + "dono" : publicUrl + "/dono";
    }

    @Transactional
    public Map<String, Object> criarCheckout(Barbearia loja, String payerEmail) {
        if (accessToken.isBlank()) {
            Map<String, Object> mock = new LinkedHashMap<>();
            mock.put("sandbox", true);
            mock.put("initPoint", "https://www.mercadopago.com.br/subscriptions/checkout?preapproval_id=sandbox-demo");
            mock.put("message", "Configure MERCADOPAGO_ACCESS_TOKEN");
            mock.put("publicKey", publicKey);
            mock.put("preco", precoPlano);
            return mock;
        }

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("reason", "Encaixe — Plano Solo");
            body.put("external_reference", "barbearia-" + loja.getId());
            body.put("payer_email", payerEmail);
            body.put("back_url", backUrl);
            body.put("status", "pending");

            Map<String, Object> recurring = new LinkedHashMap<>();
            recurring.put("frequency", 1);
            recurring.put("frequency_type", "months");
            recurring.put("transaction_amount", precoPlano);
            recurring.put("currency_id", "BRL");
            body.put("auto_recurring", recurring);

            String json = mapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(PREAPPROVAL_URL))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Mercado Pago preapproval falhou: {} {}", response.statusCode(), response.body());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível iniciar a assinatura");
            }

            JsonNode root = mapper.readTree(response.body());
            String preapprovalId = text(root, "id");
            String initPoint = text(root, "init_point");
            if (initPoint == null || initPoint.isBlank()) {
                initPoint = text(root, "sandbox_init_point");
            }

            if (preapprovalId != null) {
                loja.setMercadoPagoPreapprovalId(preapprovalId);
                barbearias.save(loja);
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("sandbox", false);
            out.put("initPoint", initPoint);
            out.put("preapprovalId", preapprovalId);
            out.put("publicKey", publicKey);
            out.put("preco", precoPlano);
            return out;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao criar checkout Mercado Pago", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Erro ao comunicar com Mercado Pago");
        }
    }

    @Transactional
    public void processarWebhook(Map<String, Object> payload) {
        try {
            String tipo = stringDe(payload.get("type"));
            if (tipo == null) tipo = stringDe(payload.get("topic"));
            String preapprovalId = extrairPreapprovalId(payload);

            if (preapprovalId == null || preapprovalId.isBlank()) {
                log.info("Webhook MP sem preapproval id: {}", payload);
                return;
            }

            Barbearia loja = barbearias.findByMercadoPagoPreapprovalId(preapprovalId).orElse(null);

            if (loja == null && accessToken.isBlank()) {
                return;
            }

            String status = stringDe(payload.get("status"));
            if ((status == null || status.isBlank()) && !accessToken.isBlank()) {
                status = consultarStatusPreapproval(preapprovalId);
            }
            if (status == null) {
                Object data = payload.get("data");
                if (data instanceof Map<?, ?> m) {
                    status = stringDe(m.get("status"));
                }
            }

            if (loja == null) {
                log.warn("Webhook MP: loja não encontrada para preapproval {}", preapprovalId);
                return;
            }

            aplicarStatus(loja, status);
            barbearias.save(loja);
            log.info("Assinatura loja {} atualizada via webhook (tipo={}, status={})", loja.getSlug(), tipo, status);
        } catch (Exception e) {
            log.error("Falha ao processar webhook Mercado Pago", e);
        }
    }

    private void aplicarStatus(Barbearia loja, String status) {
        if (status == null) return;
        String s = status.toLowerCase();
        switch (s) {
            case "authorized", "active" -> {
                loja.setStatusAssinatura(StatusAssinatura.ATIVA);
                loja.setPlano(PlanoBarbearia.SOLO);
            }
            case "paused" -> loja.setStatusAssinatura(StatusAssinatura.ATRASADA);
            case "cancelled", "canceled" -> loja.setStatusAssinatura(StatusAssinatura.CANCELADA);
            default -> log.info("Status de assinatura ignorado: {}", status);
        }
    }

    private String consultarStatusPreapproval(String id) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(PREAPPROVAL_URL + "/" + id))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return text(mapper.readTree(response.body()), "status");
            }
        } catch (Exception e) {
            log.warn("Não foi possível consultar preapproval {}: {}", id, e.getMessage());
        }
        return null;
    }

    private String extrairPreapprovalId(Map<String, Object> payload) {
        String id = stringDe(payload.get("id"));
        Object data = payload.get("data");
        if (data instanceof Map<?, ?> m) {
            String dataId = stringDe(m.get("id"));
            if (dataId != null) return dataId;
        }
        if (payload.get("preapproval_id") != null) {
            return stringDe(payload.get("preapproval_id"));
        }
        String entity = stringDe(payload.get("entity"));
        if ("preapproval".equalsIgnoreCase(entity)
                || "subscription_preapproval".equalsIgnoreCase(stringDe(payload.get("type")))) {
            return id;
        }
        return id;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String stringDe(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
