package br.com.barberini.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;

import java.util.Locale;
import java.util.Set;

/** Bloqueia boot em produção com segredos fracos (JWT, seed). */
@Configuration
@Profile("postgres")
public class ProdSecurityValidator {

    private static final Logger log = LoggerFactory.getLogger(ProdSecurityValidator.class);

    private static final Set<String> JWT_FRACOS = Set.of(
            "laminapro-segredo-dev-mude-em-producao-1234567890",
            "laminapro-jwt-troque-depois-em-producao-2026",
            "barberini-teste-segredo-com-mais-de-32-chars"
    );

    @Bean
    @Order(-100)
    CommandLineRunner validarSegurancaProducao(
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.seed.enabled:false}") boolean seedEnabled,
            @Value("${app.seed.dono.senha:}") String seedSenha,
            @Value("${app.mercadopago.access-token:}") String mpToken,
            @Value("${app.mercadopago.webhook-secret:}") String mpWebhookSecret) {
        return args -> {
            validarJwt(jwtSecret);
            if (seedEnabled) {
                validarSenhaSeed(seedSenha);
            } else {
                log.info("Seed desativado (APP_SEED_ENABLED=false) — recomendado em produção");
            }
            if (!mpToken.isBlank() && mpWebhookSecret.isBlank()) {
                log.warn(
                        "MERCADOPAGO_ACCESS_TOKEN configurado sem MERCADOPAGO_WEBHOOK_SECRET — "
                                + "configure a assinatura do webhook no painel MP e cole o secret nas envs");
            }
        };
    }

    private static void validarJwt(String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                    "APP_JWT_SECRET inválido: use pelo menos 32 caracteres aleatórios em produção");
        }
        String norm = secret.trim().toLowerCase(Locale.ROOT);
        if (JWT_FRACOS.contains(norm) || norm.contains("troque-depois") || norm.contains("dev-mude")) {
            throw new IllegalStateException(
                    "APP_JWT_SECRET fraco ou padrão de desenvolvimento — gere um segredo forte na Northflank");
        }
    }

    private static void validarSenhaSeed(String senha) {
        if (senha == null || senha.length() < 12) {
            throw new IllegalStateException(
                    "APP_SEED_DONO_SENHA fraca: com seed ativo, use senha de pelo menos 12 caracteres");
        }
        String s = senha.toLowerCase(Locale.ROOT);
        if (s.equals("dono123") || s.equals("senha123") || s.equals("password")) {
            throw new IllegalStateException(
                    "APP_SEED_DONO_SENHA não pode ser senha padrão em produção");
        }
    }
}
