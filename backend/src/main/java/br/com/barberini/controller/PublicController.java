package br.com.barberini.controller;

import br.com.barberini.model.Barbearia;
import br.com.barberini.repository.BarbeariaRepository;
import br.com.barberini.repository.ServicoRepository;
import br.com.barberini.service.AgendaService;
import br.com.barberini.service.CatalogoService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public")
public class PublicController {

    private final CatalogoService catalogo;
    private final AgendaService agenda;
    private final ServicoRepository servicos;
    private final BarbeariaRepository barbearias;

    public PublicController(
            CatalogoService catalogo,
            AgendaService agenda,
            ServicoRepository servicos,
            BarbeariaRepository barbearias) {
        this.catalogo = catalogo;
        this.agenda = agenda;
        this.servicos = servicos;
        this.barbearias = barbearias;
    }

    @GetMapping("/{slug}")
    @Transactional(readOnly = true)
    public Map<String, Object> loja(@PathVariable String slug) {
        Barbearia b = exigirLoja(slug);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId());
        m.put("nome", b.getNome());
        m.put("slug", b.getSlug());
        m.put("logoData", b.getLogoData());
        m.put("telefone", b.getTelefone());
        return m;
    }

    @GetMapping("/{slug}/barbeiros")
    public List<Map<String, Object>> barbeiros(@PathVariable String slug) {
        Barbearia b = exigirLoja(slug);
        return catalogo.listarBarbeirosDaLoja(b.getId(), true);
    }

    @GetMapping("/{slug}/servicos")
    public List<Map<String, Object>> servicos(@PathVariable String slug) {
        Barbearia b = exigirLoja(slug);
        return catalogo.listarServicosDaLoja(b.getId(), true);
    }

    @GetMapping("/{slug}/slots")
    @Transactional(readOnly = true)
    public Map<String, Object> slots(
            @PathVariable String slug,
            @RequestParam Long barbeiroId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam(required = false) Long servicoId,
            @RequestParam(required = false, defaultValue = "30") Integer duracaoMin) {
        Barbearia loja = exigirLoja(slug);
        int dur = duracaoMin;
        if (servicoId != null) {
            dur = servicos.findByIdAndBarbeariaId(servicoId, loja.getId())
                    .map(s -> s.getDuracaoMin())
                    .orElse(dur);
        }
        return Map.of(
                "barbeiroId", barbeiroId,
                "data", data.toString(),
                "duracaoMin", dur,
                "slots", agenda.slotsDisponiveis(barbeiroId, data, dur)
        );
    }

    @GetMapping("/{slug}/bloqueios")
    public List<Map<String, Object>> bloqueios(
            @PathVariable String slug,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        Barbearia b = exigirLoja(slug);
        return catalogo.listarBloqueiosDaLoja(b.getId(), data);
    }

    private Barbearia exigirLoja(String slug) {
        return barbearias.findBySlugIgnoreCase(slug.trim())
                .filter(Barbearia::isAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Loja não encontrada"));
    }
}
