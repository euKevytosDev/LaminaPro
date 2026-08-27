package br.com.barberini.service;

import br.com.barberini.dto.BarbeiroRequest;
import br.com.barberini.dto.BloqueioRequest;
import br.com.barberini.dto.SyncBloqueiosRequest;
import br.com.barberini.dto.ServicoRequest;
import br.com.barberini.model.Barbearia;
import br.com.barberini.model.Barbeiro;
import br.com.barberini.model.BloqueioHorario;
import br.com.barberini.model.Servico;
import br.com.barberini.repository.BarbeiroRepository;
import br.com.barberini.repository.BloqueioHorarioRepository;
import br.com.barberini.repository.ServicoRepository;
import br.com.barberini.security.TenantSupport;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CatalogoService {

    private final BarbeiroRepository barbeiros;
    private final ServicoRepository servicos;
    private final BloqueioHorarioRepository bloqueios;
    private final TenantSupport tenant;

    public CatalogoService(
            BarbeiroRepository barbeiros,
            ServicoRepository servicos,
            BloqueioHorarioRepository bloqueios,
            TenantSupport tenant) {
        this.barbeiros = barbeiros;
        this.servicos = servicos;
        this.bloqueios = bloqueios;
        this.tenant = tenant;
    }

    public List<Map<String, Object>> listarBarbeiros(boolean soAtivos) {
        Long lojaId = tenant.exigirDonoComLoja().getId();
        return listarBarbeirosDaLoja(lojaId, soAtivos);
    }

    public List<Map<String, Object>> listarBarbeirosDaLoja(Long barbeariaId, boolean soAtivos) {
        List<Barbeiro> lista = soAtivos
                ? barbeiros.findByBarbeariaIdAndAtivoTrueOrderByNomeAsc(barbeariaId)
                : barbeiros.findByBarbeariaIdOrderByNomeAsc(barbeariaId);
        return lista.stream().map(this::mapBarbeiro).toList();
    }

    public Map<String, Object> criarBarbeiro(BarbeiroRequest req) {
        Barbearia loja = tenant.exigirDonoComLoja();
        Barbeiro b = new Barbeiro();
        b.setBarbearia(loja);
        aplicarBarbeiro(b, req);
        return mapBarbeiro(barbeiros.save(b));
    }

    public Map<String, Object> atualizarBarbeiro(Long id, BarbeiroRequest req) {
        Barbearia loja = tenant.exigirDonoComLoja();
        Barbeiro b = barbeiros.findByIdAndBarbeariaId(id, loja.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Barbeiro não encontrado"));
        aplicarBarbeiro(b, req);
        return mapBarbeiro(barbeiros.save(b));
    }

    public List<Map<String, Object>> listarServicos(boolean soAtivos) {
        Long lojaId = tenant.exigirDonoComLoja().getId();
        return listarServicosDaLoja(lojaId, soAtivos);
    }

    public List<Map<String, Object>> listarServicosDaLoja(Long barbeariaId, boolean soAtivos) {
        List<Servico> lista = soAtivos
                ? servicos.findByBarbeariaIdAndAtivoTrueOrderByNomeAsc(barbeariaId)
                : servicos.findByBarbeariaIdOrderByNomeAsc(barbeariaId);
        return lista.stream().map(this::mapServico).toList();
    }

    public Map<String, Object> criarServico(ServicoRequest req) {
        Barbearia loja = tenant.exigirDonoComLoja();
        Servico s = new Servico();
        s.setBarbearia(loja);
        aplicarServico(s, req);
        return mapServico(servicos.save(s));
    }

    public Map<String, Object> atualizarServico(Long id, ServicoRequest req) {
        Barbearia loja = tenant.exigirDonoComLoja();
        Servico s = servicos.findByIdAndBarbeariaId(id, loja.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Serviço não encontrado"));
        aplicarServico(s, req);
        return mapServico(servicos.save(s));
    }

    @Transactional
    public Map<String, Object> criarBloqueio(BloqueioRequest req) {
        Barbearia loja = tenant.exigirDonoComLoja();
        BloqueioHorario b = new BloqueioHorario();
        b.setBarbearia(loja);
        b.setData(req.data());
        b.setHora(req.hora());
        b.setMotivo(req.motivo());
        if (req.barbeiroId() != null) {
            Barbeiro barb = barbeiros.findByIdAndBarbeariaId(req.barbeiroId(), loja.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Barbeiro não encontrado"));
            b.setBarbeiro(barb);
        }
        return mapBloqueio(bloqueios.save(b));
    }

    public void removerBloqueio(Long id) {
        Barbearia loja = tenant.exigirDonoComLoja();
        BloqueioHorario b = bloqueios.findByIdAndBarbeariaId(id, loja.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bloqueio não encontrado"));
        bloqueios.delete(b);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listarBloqueios(java.time.LocalDate data) {
        Long lojaId = tenant.exigirDonoComLoja().getId();
        return listarBloqueiosDaLoja(lojaId, data);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listarBloqueiosDaLoja(Long barbeariaId, java.time.LocalDate data) {
        return bloqueios.findByBarbeariaIdAndData(barbeariaId, data).stream().map(this::mapBloqueio).toList();
    }

    @Transactional
    public List<Map<String, Object>> syncBloqueios(SyncBloqueiosRequest req) {
        Barbearia loja = tenant.exigirDonoComLoja();
        Long lojaId = loja.getId();

        if (req.removerIds() != null) {
            for (Long id : req.removerIds()) {
                if (id == null) continue;
                bloqueios.findByIdAndBarbeariaId(id, lojaId).ifPresent(bloqueios::delete);
            }
        }

        Barbeiro barbeiro = null;
        if (req.barbeiroId() != null) {
            barbeiro = barbeiros.findByIdAndBarbeariaId(req.barbeiroId(), lojaId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Barbeiro não encontrado"));
        }

        if (req.bloqueios() != null) {
            for (SyncBloqueiosRequest.BloqueioItem item : req.bloqueios()) {
                BloqueioHorario b = new BloqueioHorario();
                b.setBarbearia(loja);
                b.setData(req.data());
                b.setHora(item.hora());
                b.setMotivo(item.motivo());
                b.setBarbeiro(barbeiro);
                bloqueios.save(b);
            }
        }
        return listarBloqueiosDaLoja(lojaId, req.data());
    }

    private void aplicarBarbeiro(Barbeiro b, BarbeiroRequest req) {
        b.setNome(req.nome().trim());
        String ini = req.iniciais() != null && !req.iniciais().isBlank()
                ? req.iniciais().trim().toUpperCase()
                : iniciaisDe(req.nome());
        b.setIniciais(ini.substring(0, Math.min(4, ini.length())));
        b.setCor(req.cor() != null && !req.cor().isBlank() ? req.cor() : "#3d3d3d");
        if (req.ativo() != null) b.setAtivo(req.ativo());
        if (req.fotoData() != null) {
            b.setFotoData(req.fotoData().isBlank() ? null : req.fotoData());
        }
    }

    private void aplicarServico(Servico s, ServicoRequest req) {
        s.setNome(req.nome().trim());
        s.setPreco(req.preco());
        s.setDuracaoMin(req.duracaoMin());
        if (req.ativo() != null) s.setAtivo(req.ativo());
    }

    private String iniciaisDe(String nome) {
        String[] p = nome.trim().split("\\s+");
        if (p.length == 1) return p[0].substring(0, Math.min(2, p[0].length())).toUpperCase();
        return ("" + p[0].charAt(0) + p[p.length - 1].charAt(0)).toUpperCase();
    }

    public Map<String, Object> mapBarbeiro(Barbeiro b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId());
        m.put("nome", b.getNome());
        m.put("iniciais", b.getIniciais());
        m.put("cor", b.getCor());
        m.put("ativo", b.isAtivo());
        m.put("fotoData", b.getFotoData());
        return m;
    }

    public Map<String, Object> mapServico(Servico s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("nome", s.getNome());
        m.put("preco", s.getPreco());
        m.put("duracaoMin", s.getDuracaoMin());
        m.put("ativo", s.isAtivo());
        return m;
    }

    private Map<String, Object> mapBloqueio(BloqueioHorario b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId());
        m.put("data", b.getData().toString());
        m.put("hora", b.getHora().toString().substring(0, 5));
        m.put("motivo", b.getMotivo());
        m.put("barbeiroId", b.getBarbeiro() != null ? b.getBarbeiro().getId() : null);
        m.put("barbeiroNome", b.getBarbeiro() != null ? b.getBarbeiro().getNome() : "Todos");
        return m;
    }
}
