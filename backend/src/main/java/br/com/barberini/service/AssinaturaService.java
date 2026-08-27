package br.com.barberini.service;

import br.com.barberini.model.Barbearia;
import br.com.barberini.model.PlanoBarbearia;
import br.com.barberini.model.StatusAssinatura;
import br.com.barberini.repository.BarbeariaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AssinaturaService {

    private static final Logger log = LoggerFactory.getLogger(AssinaturaService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final BarbeariaRepository barbearias;
    private final MercadoPagoClient mercadoPago;
    private final String frontUrl;
    private final String webhookUrl;
    private final int trialDias;

    public AssinaturaService(
            BarbeariaRepository barbearias,
            MercadoPagoClient mercadoPago,
            @Value("${app.public-url:https://eukevytosdev.github.io/LaminaPro}") String publicUrl,
            @Value("${app.assinatura.webhook-url:}") String webhookUrl,
            @Value("${app.assinatura.trial-dias:14}") int trialDias) {
        this.barbearias = barbearias;
        this.mercadoPago = mercadoPago;
        String base = publicUrl == null ? "" : publicUrl.trim();
        if (!base.endsWith("/")) base = base + "/";
        this.frontUrl = base;
        this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
        this.trialDias = trialDias > 0 ? trialDias : 14;
    }

    public List<Map<String, Object>> catalogo() {
        return CatalogoPlanos.publico();
    }

    public Map<String, Object> statusLoja(Barbearia loja) {
        Map<String, Object> m = new LinkedHashMap<>();
        PlanoBarbearia plano = loja.getPlano() != null ? loja.getPlano().normalizado() : PlanoBarbearia.TRIAL;
        boolean ativa = assinaturaAtiva(loja);
        m.put("plano", plano.name());
        m.put("planoRotulo", plano.rotulo());
        m.put("periodo", loja.getPlanoPeriodo() != null ? loja.getPlanoPeriodo() : "");
        m.put("status", loja.getStatusAssinatura() != null ? loja.getStatusAssinatura().name() : "TRIAL");
        m.put("ativa", ativa);
        m.put("bloqueada", !ativa);
        m.put("maxBarbeiros", CatalogoPlanos.maxBarbeiros(loja.getPlano()));
        m.put("checkoutWeb", mercadoPago.configurado());
        m.put("trialDias", trialDias);
        LocalDateTime trialFim = null;
        if (loja.getStatusAssinatura() == StatusAssinatura.TRIAL) {
            LocalDateTime base = loja.getCriadoEm() != null ? loja.getCriadoEm() : LocalDateTime.now();
            trialFim = base.plusDays(trialDias);
        }
        m.put("trialExpiraEm", trialFim != null ? trialFim.toString() : "");
        m.put("trialExpiraEmTexto", trialFim != null ? FMT.format(trialFim) : "");
        LocalDateTime exp = loja.getPlanoExpiraEm();
        m.put("expiraEm", exp != null ? exp.toString() : "");
        m.put("expiraEmTexto", exp != null ? FMT.format(exp) : "");
        m.put("origem", loja.getPagamentoOrigem() != null ? loja.getPagamentoOrigem() : "");
        return m;
    }

    /** Após o trial, só libera recursos com assinatura ativa (pagamento). */
    public void exigirAssinaturaAtiva(Barbearia loja) {
        if (assinaturaAtiva(loja)) return;
        throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                "Seu período de teste acabou. Assine um plano para continuar usando o Lâmina Pro.");
    }

    public boolean assinaturaAtiva(Barbearia loja) {
        if (loja == null) return false;
        StatusAssinatura st = loja.getStatusAssinatura();
        if (st == StatusAssinatura.ATIVA) {
            LocalDateTime exp = loja.getPlanoExpiraEm();
            if (exp == null) return true; // recorrente sem data fixa
            return exp.isAfter(LocalDateTime.now());
        }
        if (st == StatusAssinatura.TRIAL) {
            LocalDateTime base = loja.getCriadoEm() != null ? loja.getCriadoEm() : LocalDateTime.now();
            return base.plusDays(trialDias).isAfter(LocalDateTime.now());
        }
        return false;
    }

    @Transactional
    public Map<String, Object> criarCheckout(Barbearia loja, String payerEmail, String planoId) {
        CatalogoPlanos.Item plano = CatalogoPlanos.resolver(planoId);
        if (!mercadoPago.configurado()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Pagamento ainda não está ligado. Seu trial segue ativo por enquanto.");
        }

        String externalRef = loja.getId() + ":" + plano.id();
        String success = frontUrl + "#/dono?pago=ok";
        String fail = frontUrl + "#/dono?pago=falhou";
        String notify = webhookUrl.isBlank() ? null : webhookUrl;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("planoId", plano.id());
        out.put("preco", plano.valor());
        out.put("checkoutWeb", true);

        if (plano.recorrente()) {
            Map<String, Object> sub = mercadoPago.criarAssinaturaRecorrente(
                    "Lâmina Pro — " + plano.nome(),
                    plano.valor(),
                    externalRef,
                    payerEmail,
                    success,
                    notify
            );
            Object init = sub.get("init_point");
            if (init == null) init = sub.get("sandbox_init_point");
            Object preId = sub.get("id");
            if (preId != null) {
                loja.setMercadoPagoPreapprovalId(String.valueOf(preId));
                barbearias.save(loja);
            }
            out.put("initPoint", init);
            out.put("preapprovalId", preId);
            out.put("tipo", "recorrente");
            return out;
        }

        Map<String, Object> pref = mercadoPago.criarPreferencia(
                "Lâmina Pro — " + plano.nome(),
                plano.valor(),
                externalRef,
                notify,
                success,
                fail
        );
        out.put("initPoint", pref.get("init_point"));
        out.put("preferenceId", pref.get("id"));
        out.put("tipo", "avulso");
        return out;
    }

    @Transactional
    public void processarNotificacaoPagamento(String paymentId) {
        if (paymentId == null || paymentId.isBlank()) return;
        Map<String, Object> pag = mercadoPago.buscarPagamento(paymentId.trim());
        if (pag == null) return;
        String status = String.valueOf(pag.getOrDefault("status", ""));
        if (!"approved".equalsIgnoreCase(status)) return;

        String ext = String.valueOf(pag.getOrDefault("external_reference", ""));
        String planoId = extrairPlanoId(ext);
        if (planoId == null) {
            Object preId = pag.get("preapproval_id");
            if (preId != null) processarAssinatura(String.valueOf(preId));
            return;
        }
        ativarPorPagamento(ext, CatalogoPlanos.resolver(planoId), paymentId.trim(), "WEB_MP");
    }

    @Transactional
    public void processarAssinatura(String preapprovalId) {
        if (preapprovalId == null || preapprovalId.isBlank()) return;
        Map<String, Object> sub = mercadoPago.buscarPreapproval(preapprovalId.trim());
        if (sub == null) return;

        String status = String.valueOf(sub.getOrDefault("status", ""));
        if (!"authorized".equalsIgnoreCase(status) && !"active".equalsIgnoreCase(status)) {
            if ("paused".equalsIgnoreCase(status) || "cancelled".equalsIgnoreCase(status)
                    || "canceled".equalsIgnoreCase(status)) {
                Barbearia loja = barbearias.findByMercadoPagoPreapprovalId(preapprovalId.trim()).orElse(null);
                if (loja != null) {
                    loja.setStatusAssinatura(
                            "paused".equalsIgnoreCase(status)
                                    ? StatusAssinatura.ATRASADA
                                    : StatusAssinatura.CANCELADA);
                    barbearias.save(loja);
                }
            }
            return;
        }

        String ext = String.valueOf(sub.getOrDefault("external_reference", ""));
        String planoId = extrairPlanoId(ext);
        if (planoId == null) return;
        CatalogoPlanos.Item plano = CatalogoPlanos.resolver(planoId);
        if (!plano.recorrente()) return;

        long lojaId = extrairLojaId(ext);
        if (lojaId <= 0) return;
        Barbearia loja = barbearias.findById(lojaId).orElse(null);
        if (loja == null) {
            loja = barbearias.findByMercadoPagoPreapprovalId(preapprovalId.trim()).orElse(null);
        }
        if (loja == null) return;

        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime base = loja.getPlanoExpiraEm();
        if (base == null || base.isBefore(agora)) base = agora;

        loja.setPlano(plano.faixa());
        loja.setPlanoPeriodo(plano.periodo());
        loja.setPlanoExpiraEm(base.plusDays(35));
        loja.setStatusAssinatura(StatusAssinatura.ATIVA);
        loja.setPagamentoOrigem("WEB_MP_RECORRENTE");
        loja.setMercadoPagoPreapprovalId(preapprovalId.trim());
        barbearias.save(loja);
        log.info("Assinatura recorrente ativada loja {} plano {}", loja.getSlug(), plano.id());
    }

    private void ativarPorPagamento(String ext, CatalogoPlanos.Item plano, String paymentId, String origem) {
        long lojaId = extrairLojaId(ext);
        if (lojaId <= 0) return;
        Barbearia loja = barbearias.findById(lojaId).orElse(null);
        if (loja == null) return;
        if (paymentId.equals(loja.getUltimoPagamentoMp())) return;

        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime base = loja.getPlanoExpiraEm();
        if (base == null || base.isBefore(agora)) base = agora;

        loja.setPlano(plano.faixa());
        loja.setPlanoPeriodo(plano.periodo());
        loja.setPlanoExpiraEm(base.plusDays(plano.dias()));
        loja.setStatusAssinatura(StatusAssinatura.ATIVA);
        loja.setPagamentoOrigem(origem);
        loja.setUltimoPagamentoMp(paymentId);
        barbearias.save(loja);
        log.info("Assinatura avulsa ativada loja {} plano {} pagamento {}", loja.getSlug(), plano.id(), paymentId);
    }

    private static String extrairPlanoId(String ext) {
        int sep = ext == null ? -1 : ext.indexOf(':');
        if (sep <= 0 || sep >= ext.length() - 1) return null;
        return ext.substring(sep + 1);
    }

    private static long extrairLojaId(String ext) {
        int sep = ext == null ? -1 : ext.indexOf(':');
        if (sep <= 0) return -1;
        try {
            return Long.parseLong(ext.substring(0, sep));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
