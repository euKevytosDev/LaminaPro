package br.com.barberini.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Valida x-signature do Mercado Pago (HMAC-SHA256). */
@Component
public class MercadoPagoWebhookValidator {

    private final String webhookSecret;

    public MercadoPagoWebhookValidator(
            @Value("${app.mercadopago.webhook-secret:}") String webhookSecret) {
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
    }

    public boolean configurado() {
        return !webhookSecret.isBlank();
    }

    /**
     * @param xSignature header x-signature (ts=...,v1=...)
     * @param xRequestId header x-request-id
     * @param dataId     valor de data.id (query ou body) — manter casing original
     */
    public boolean valido(String xSignature, String xRequestId, String dataId) {
        if (!configurado()) return true;
        if (xSignature == null || xSignature.isBlank()) return false;

        Map<String, String> parts = new TreeMap<>();
        for (String piece : xSignature.split(",")) {
            String[] kv = piece.trim().split("=", 2);
            if (kv.length == 2) parts.put(kv[0].trim(), kv[1].trim());
        }
        String ts = parts.get("ts");
        String v1 = parts.get("v1");
        if (ts == null || v1 == null) return false;

        String id = dataId == null ? "" : dataId;
        String reqId = xRequestId == null ? "" : xRequestId;
        String manifest = "id:" + id + ";request-id:" + reqId + ";ts:" + ts + ";";
        String esperado = hmacSha256Hex(webhookSecret, manifest);
        return MessageDigest.isEqual(
                esperado.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
                v1.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    private static String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
