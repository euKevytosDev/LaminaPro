package br.com.barberini.controller;

import br.com.barberini.dto.AtualizarLojaRequest;
import br.com.barberini.dto.AtualizarStatusRequest;
import br.com.barberini.dto.BarbeiroRequest;
import br.com.barberini.dto.BloqueioRequest;
import br.com.barberini.dto.ReatribuirBarbeiroRequest;
import br.com.barberini.dto.ServicoRequest;
import br.com.barberini.dto.SyncBloqueiosRequest;
import br.com.barberini.model.Barbearia;
import br.com.barberini.repository.BarbeariaRepository;
import br.com.barberini.security.TenantSupport;
import br.com.barberini.service.AgendamentoService;
import br.com.barberini.service.CatalogoService;
import br.com.barberini.service.ResumoService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dono")
public class DonoController {

    private final CatalogoService catalogo;
    private final AgendamentoService agendamentos;
    private final ResumoService resumos;
    private final TenantSupport tenant;
    private final BarbeariaRepository barbearias;

    public DonoController(
            CatalogoService catalogo,
            AgendamentoService agendamentos,
            ResumoService resumos,
            TenantSupport tenant,
            BarbeariaRepository barbearias) {
        this.catalogo = catalogo;
        this.agendamentos = agendamentos;
        this.resumos = resumos;
        this.tenant = tenant;
        this.barbearias = barbearias;
    }

    @GetMapping("/loja")
    @Transactional(readOnly = true)
    public Map<String, Object> loja() {
        return mapLoja(tenant.exigirDonoComLoja());
    }

    @PutMapping("/loja")
    @Transactional
    public Map<String, Object> atualizarLoja(@Valid @RequestBody AtualizarLojaRequest req) {
        Barbearia loja = tenant.exigirDonoComLoja();
        loja.setNome(req.nome().trim());
        if (req.telefone() != null) {
            loja.setTelefone(req.telefone().isBlank() ? null : req.telefone().trim());
        }
        if (req.logoData() != null) {
            loja.setLogoData(req.logoData().isBlank() ? null : req.logoData());
        }
        return mapLoja(barbearias.save(loja));
    }

    @GetMapping("/agendamentos")
    public List<Map<String, Object>> agenda(
            @RequestParam(defaultValue = "30") int dias,
            @RequestParam(defaultValue = "7") int diasAtras) {
        return agendamentos.todosProximos(dias, diasAtras);
    }

    @GetMapping("/resumo")
    public Map<String, Object> resumo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return resumos.resumo(inicio, fim);
    }

    @PutMapping("/agendamentos/{id}/barbeiro")
    public Map<String, Object> reatribuirBarbeiro(
            @PathVariable Long id, @Valid @RequestBody ReatribuirBarbeiroRequest req) {
        return agendamentos.reatribuirBarbeiro(id, req.barbeiroId());
    }

    @PutMapping("/agendamentos/{id}/status")
    public Map<String, Object> atualizarStatus(
            @PathVariable Long id, @Valid @RequestBody AtualizarStatusRequest req) {
        return agendamentos.atualizarStatus(id, req.status(), req.valorCobrado());
    }

    @GetMapping("/barbeiros")
    public List<Map<String, Object>> barbeiros() {
        return catalogo.listarBarbeiros(false);
    }

    @PostMapping("/barbeiros")
    public Map<String, Object> criarBarbeiro(@Valid @RequestBody BarbeiroRequest req) {
        return catalogo.criarBarbeiro(req);
    }

    @PutMapping("/barbeiros/{id}")
    public Map<String, Object> atualizarBarbeiro(@PathVariable Long id, @Valid @RequestBody BarbeiroRequest req) {
        return catalogo.atualizarBarbeiro(id, req);
    }

    @GetMapping("/servicos")
    public List<Map<String, Object>> servicos() {
        return catalogo.listarServicos(false);
    }

    @PostMapping("/servicos")
    public Map<String, Object> criarServico(@Valid @RequestBody ServicoRequest req) {
        return catalogo.criarServico(req);
    }

    @PutMapping("/servicos/{id}")
    public Map<String, Object> atualizarServico(@PathVariable Long id, @Valid @RequestBody ServicoRequest req) {
        return catalogo.atualizarServico(id, req);
    }

    @GetMapping("/bloqueios")
    public List<Map<String, Object>> bloqueios(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return catalogo.listarBloqueios(data);
    }

    @PostMapping("/bloqueios")
    public Map<String, Object> criarBloqueio(@Valid @RequestBody BloqueioRequest req) {
        return catalogo.criarBloqueio(req);
    }

    @PostMapping("/bloqueios/sync")
    public List<Map<String, Object>> syncBloqueios(@Valid @RequestBody SyncBloqueiosRequest req) {
        return catalogo.syncBloqueios(req);
    }

    @DeleteMapping("/bloqueios/{id}")
    public Map<String, String> removerBloqueio(@PathVariable Long id) {
        catalogo.removerBloqueio(id);
        return Map.of("message", "Horário reativado");
    }

    private Map<String, Object> mapLoja(Barbearia b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId());
        m.put("nome", b.getNome());
        m.put("slug", b.getSlug());
        m.put("telefone", b.getTelefone());
        m.put("logoData", b.getLogoData());
        m.put("plano", b.getPlano().name());
        m.put("statusAssinatura", b.getStatusAssinatura().name());
        return m;
    }
}
