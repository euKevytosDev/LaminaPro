package br.com.barberini.service;

import br.com.barberini.dto.CriarAgendamentoRequest;
import br.com.barberini.model.*;
import br.com.barberini.repository.AgendamentoRepository;
import br.com.barberini.repository.BarbeariaRepository;
import br.com.barberini.repository.BarbeiroRepository;
import br.com.barberini.repository.ServicoRepository;
import br.com.barberini.repository.UsuarioRepository;
import br.com.barberini.security.AuthSupport;
import br.com.barberini.security.TenantSupport;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AgendamentoService {

    private final AgendamentoRepository agendamentos;
    private final BarbeariaRepository barbearias;
    private final BarbeiroRepository barbeiros;
    private final ServicoRepository servicos;
    private final UsuarioRepository usuarios;
    private final AgendaService agenda;
    private final TenantSupport tenant;

    public AgendamentoService(
            AgendamentoRepository agendamentos,
            BarbeariaRepository barbearias,
            BarbeiroRepository barbeiros,
            ServicoRepository servicos,
            UsuarioRepository usuarios,
            AgendaService agenda,
            TenantSupport tenant) {
        this.agendamentos = agendamentos;
        this.barbearias = barbearias;
        this.barbeiros = barbeiros;
        this.servicos = servicos;
        this.usuarios = usuarios;
        this.agenda = agenda;
        this.tenant = tenant;
    }

    @Transactional
    public Map<String, Object> criar(CriarAgendamentoRequest req) {
        Long clienteId = AuthSupport.atual().getId();
        Usuario cliente = usuarios.findById(clienteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário inválido"));

        Barbearia loja = barbearias.findBySlugIgnoreCase(req.slug().trim())
                .filter(Barbearia::isAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Loja não encontrada"));

        Servico servico = servicos.findByIdAndBarbeariaId(req.servicoId(), loja.getId())
                .filter(Servico::isAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Serviço indisponível"));

        String horaStr = req.horaInicio().toString().substring(0, 5);
        boolean semPreferencia = Boolean.TRUE.equals(req.semPreferencia()) || req.barbeiroId() == null;

        Barbeiro barbeiro;
        if (semPreferencia) {
            barbeiro = sortearBarbeiroDisponivel(loja.getId(), req.data(), horaStr, servico.getDuracaoMin());
        } else {
            barbeiro = barbeiros.findByIdAndBarbeariaId(req.barbeiroId(), loja.getId())
                    .filter(Barbeiro::isAtivo)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Barbeiro indisponível"));
            List<String> livres = agenda.slotsDisponiveis(barbeiro.getId(), req.data(), servico.getDuracaoMin());
            if (!livres.contains(horaStr)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Horário indisponível. Escolha outro.");
            }
        }

        LocalTime fim = agenda.calcularFim(req.horaInicio(), servico.getDuracaoMin());
        Agendamento a = new Agendamento();
        a.setBarbearia(loja);
        a.setCliente(cliente);
        a.setBarbeiro(barbeiro);
        a.setServico(servico);
        a.setData(req.data());
        a.setHoraInicio(req.horaInicio());
        a.setHoraFim(fim);
        a.setObservacao(req.observacao());
        a.setStatus(StatusAgendamento.CONFIRMADO);
        a.setSemPreferencia(semPreferencia);
        a.setPrecoCobrado(servico.getPreco());
        return map(agendamentos.save(a));
    }

    /** Encaixe automático: entre os barbeiros livres no horário da loja, escolhe um aleatório. */
    private Barbeiro sortearBarbeiroDisponivel(Long barbeariaId, LocalDate data, String horaStr, int duracaoMin) {
        List<Barbeiro> candidatos = new ArrayList<>();
        for (Barbeiro b : barbeiros.findByBarbeariaIdAndAtivoTrueOrderByNomeAsc(barbeariaId)) {
            if (agenda.slotsDisponiveis(b.getId(), data, duracaoMin).contains(horaStr)) {
                candidatos.add(b);
            }
        }
        if (candidatos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Nenhum profissional disponível nesse horário.");
        }
        return candidatos.get(ThreadLocalRandom.current().nextInt(candidatos.size()));
    }

    @Transactional
    public Map<String, Object> reatribuirBarbeiro(Long id, Long novoBarbeiroId) {
        Barbearia loja = tenant.exigirDonoComLoja();
        Agendamento a = agendamentos.findByIdAndBarbeariaId(id, loja.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agendamento não encontrado"));
        if (a.getStatus() == StatusAgendamento.CANCELADO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Agendamento cancelado");
        }
        Barbeiro novo = barbeiros.findByIdAndBarbeariaId(novoBarbeiroId, loja.getId())
                .filter(Barbeiro::isAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Barbeiro indisponível"));

        if (!novo.getId().equals(a.getBarbeiro().getId())) {
            String horaStr = a.getHoraInicio().toString().substring(0, 5);
            List<String> livres = agenda.slotsDisponiveis(novo.getId(), a.getData(), a.getServico().getDuracaoMin());
            if (!livres.contains(horaStr)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Profissional ocupado nesse horário.");
            }
        }

        a.setBarbeiro(novo);
        a.setSemPreferencia(false);
        return map(agendamentos.save(a));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> meus() {
        Long id = AuthSupport.atual().getId();
        LocalDate hoje = LocalDate.now();
        return agendamentos
                .findDoClienteExcluindoStatus(id, StatusAgendamento.CANCELADO)
                .stream()
                .filter(a -> !a.getData().isBefore(hoje))
                .map(this::map)
                .toList();
    }

    /** Agenda do dono: inclui dias passados para que atendimentos em aberto possam ser fechados. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> todosProximos(int dias, int diasAtras) {
        Barbearia loja = tenant.exigirDonoComLoja();
        LocalDate hoje = LocalDate.now();
        return agendamentos
                .findByBarbeariaIdAndPeriodoExcluindoStatus(
                        loja.getId(),
                        hoje.minusDays(Math.max(0, diasAtras)),
                        hoje.plusDays(dias),
                        StatusAgendamento.CANCELADO)
                .stream()
                .map(this::map)
                .toList();
    }

    /** Dono fecha o atendimento: finalizado (com valor/forma opcionais) ou não compareceu. */
    @Transactional
    public Map<String, Object> atualizarStatus(
            Long id,
            StatusAgendamento novoStatus,
            BigDecimal valorCobrado,
            FormaPagamento formaPagamento) {
        Barbearia loja = tenant.exigirDonoComLoja();
        if (novoStatus == StatusAgendamento.CANCELADO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use o cancelamento para cancelar");
        }
        Agendamento a = agendamentos.findByIdComDetalhes(id)
                .filter(ag -> ag.getBarbearia().getId().equals(loja.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agendamento não encontrado"));
        if (a.getStatus() == StatusAgendamento.CANCELADO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Agendamento cancelado");
        }

        a.setStatus(novoStatus);
        if (novoStatus == StatusAgendamento.FINALIZADO) {
            if (valorCobrado != null) {
                if (valorCobrado.compareTo(BigDecimal.ZERO) < 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor inválido");
                }
                a.setPrecoCobrado(valorCobrado);
            }
            if (formaPagamento != null) {
                a.setFormaPagamento(formaPagamento);
            }
        }
        return map(agendamentos.save(a));
    }

    @Transactional
    public void cancelar(Long id) {
        Agendamento a = agendamentos.findByIdComDetalhes(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agendamento não encontrado"));
        var user = AuthSupport.atual();
        boolean dono = user.isDono()
                && user.getBarbeariaId() != null
                && user.getBarbeariaId().equals(a.getBarbearia().getId());
        boolean donoDoAg = a.getCliente().getId().equals(user.getId());
        if (!dono && !donoDoAg) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Não pode cancelar este agendamento");
        }
        a.setStatus(StatusAgendamento.CANCELADO);
        agendamentos.save(a);
    }

    private Map<String, Object> map(Agendamento a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("data", a.getData().toString());
        m.put("hora", a.getHoraInicio().toString().substring(0, 5));
        m.put("fim", a.getHoraFim().toString().substring(0, 5));
        m.put("observacao", a.getObservacao());
        m.put("status", a.getStatus().name());
        m.put("semPreferencia", a.isSemPreferencia());
        m.put("clienteNome", a.getCliente().getNome());
        m.put("clienteEmail", a.getCliente().getEmail());
        m.put("barbeiroId", a.getBarbeiro().getId());
        m.put("barbeiroNome", a.getBarbeiro().getNome());
        m.put("barbeiroIniciais", a.getBarbeiro().getIniciais());
        m.put("barbeiroCor", a.getBarbeiro().getCor());
        m.put("servicoId", a.getServico().getId());
        m.put("servicoNome", a.getServico().getNome());
        m.put("servicoPreco", a.getServico().getPreco());
        m.put("servicoDuracao", a.getServico().getDuracaoMin());
        m.put("valorCobrado", a.getPrecoCobrado() != null ? a.getPrecoCobrado() : a.getServico().getPreco());
        m.put("formaPagamento", a.getFormaPagamento() != null ? a.getFormaPagamento().name() : null);
        if (a.getBarbearia() != null) {
            m.put("barbeariaId", a.getBarbearia().getId());
            m.put("slug", a.getBarbearia().getSlug());
        }
        return m;
    }
}
