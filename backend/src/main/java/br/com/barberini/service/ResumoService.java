package br.com.barberini.service;

import br.com.barberini.model.Agendamento;
import br.com.barberini.model.Barbearia;
import br.com.barberini.model.Barbeiro;
import br.com.barberini.model.BloqueioHorario;
import br.com.barberini.model.StatusAgendamento;
import br.com.barberini.repository.AgendamentoRepository;
import br.com.barberini.repository.BarbeiroRepository;
import br.com.barberini.repository.BloqueioHorarioRepository;
import br.com.barberini.security.TenantSupport;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Dashboard do dono: KPIs do período, performance por barbeiro e série temporal. */
@Service
public class ResumoService {

    private static final int MAX_DIAS = 366;

    private final AgendamentoRepository agendamentos;
    private final BarbeiroRepository barbeiros;
    private final BloqueioHorarioRepository bloqueios;
    private final AgendaService agenda;
    private final TenantSupport tenant;

    public ResumoService(
            AgendamentoRepository agendamentos,
            BarbeiroRepository barbeiros,
            BloqueioHorarioRepository bloqueios,
            AgendaService agenda,
            TenantSupport tenant) {
        this.agendamentos = agendamentos;
        this.barbeiros = barbeiros;
        this.bloqueios = bloqueios;
        this.agenda = agenda;
        this.tenant = tenant;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> resumo(LocalDate inicio, LocalDate fim) {
        Barbearia loja = tenant.exigirDonoComLoja();
        Long lojaId = loja.getId();

        if (inicio == null) inicio = LocalDate.now();
        if (fim == null) fim = inicio;
        if (fim.isBefore(inicio)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data final antes da inicial");
        }
        long dias = ChronoUnit.DAYS.between(inicio, fim) + 1;
        if (dias > MAX_DIAS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Período máximo de 1 ano");
        }

        LocalDateTime agora = LocalDateTime.now();
        List<Agendamento> todos = agendamentos.findByBarbeariaIdAndPeriodo(lojaId, inicio, fim);
        List<Barbeiro> ativos = barbeiros.findByBarbeariaIdAndAtivoTrueOrderByNomeAsc(lojaId);

        Acumulador geral = new Acumulador();
        Map<Long, Acumulador> porBarbeiro = new LinkedHashMap<>();
        Map<String, Acumulador> porServico = new LinkedHashMap<>();
        Map<LocalDate, Acumulador> porDia = new TreeMap<>();
        Set<Long> clientesAtendidos = new HashSet<>();

        for (Barbeiro b : ativos) {
            porBarbeiro.put(b.getId(), new Acumulador());
        }

        for (Agendamento a : todos) {
            Situacao s = situacao(a, agora);
            BigDecimal valor = valorDe(a);
            int blocos = blocosDe(a);

            geral.somar(s, valor, blocos);
            porBarbeiro.computeIfAbsent(a.getBarbeiro().getId(), k -> new Acumulador()).somar(s, valor, blocos);
            porServico.computeIfAbsent(a.getServico().getNome(), k -> new Acumulador()).somar(s, valor, blocos);
            porDia.computeIfAbsent(a.getData(), k -> new Acumulador()).somar(s, valor, blocos);

            if (s == Situacao.REALIZADO) {
                clientesAtendidos.add(a.getCliente().getId());
            }
        }

        int slotsTotais = capacidadeSlots(lojaId, inicio, fim, ativos.size());
        int slotsPorBarbeiro = ativos.isEmpty() ? 0 : slotsTotais / ativos.size();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("inicio", inicio.toString());
        out.put("fim", fim.toString());
        out.put("dias", dias);
        out.put("realizado", Map.of(
                "atendimentos", geral.realizados,
                "faturamento", geral.faturamento,
                "ticketMedio", ticket(geral.faturamento, geral.realizados)));
        out.put("previsto", Map.of(
                "atendimentos", geral.previstos,
                "faturamento", geral.previsto));
        out.put("naoCompareceu", Map.of(
                "atendimentos", geral.noShows,
                "valorPerdido", geral.perdido));
        out.put("cancelados", geral.cancelados);
        out.put("taxaNoShow", percentual(geral.noShows, geral.realizados + geral.noShows));
        out.put("ocupacao", percentual(geral.slotsOcupados, slotsTotais));
        out.put("slotsTotais", slotsTotais);
        out.put("slotsOcupados", geral.slotsOcupados);
        out.put("clientes", clientes(lojaId, clientesAtendidos, inicio));
        out.put("porBarbeiro", listaBarbeiros(ativos, porBarbeiro, slotsPorBarbeiro));
        out.put("porServico", listaServicos(porServico));
        out.put("serie", serie(porDia, inicio, fim, dias));
        return out;
    }

    private Map<String, Object> clientes(Long barbeariaId, Set<Long> atendidos, LocalDate inicio) {
        int total = atendidos.size();
        int recorrentes = 0;
        if (total > 0) {
            recorrentes = agendamentos.clientesComHistoricoAntesNaLoja(
                    barbeariaId, new ArrayList<>(atendidos), inicio, StatusAgendamento.CANCELADO).size();
        }
        return Map.of(
                "atendidos", total,
                "novos", total - recorrentes,
                "recorrentes", recorrentes);
    }

    private List<Map<String, Object>> listaBarbeiros(
            List<Barbeiro> ativos, Map<Long, Acumulador> dados, int slotsPorBarbeiro) {
        Map<Long, Barbeiro> porId = new LinkedHashMap<>();
        for (Barbeiro b : ativos) porId.put(b.getId(), b);

        List<Map<String, Object>> lista = new ArrayList<>();
        for (Map.Entry<Long, Acumulador> e : dados.entrySet()) {
            Barbeiro b = porId.get(e.getKey());
            if (b == null) continue;
            Acumulador ac = e.getValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("barbeiroId", b.getId());
            m.put("nome", b.getNome());
            m.put("iniciais", b.getIniciais());
            m.put("cor", b.getCor());
            m.put("atendimentos", ac.realizados);
            m.put("faturamento", ac.faturamento);
            m.put("ticketMedio", ticket(ac.faturamento, ac.realizados));
            m.put("previstos", ac.previstos);
            m.put("naoCompareceu", ac.noShows);
            m.put("ocupacao", percentual(ac.slotsOcupados, slotsPorBarbeiro));
            lista.add(m);
        }
        lista.sort(Comparator.comparing((Map<String, Object> m) -> (BigDecimal) m.get("faturamento")).reversed());
        return lista;
    }

    private List<Map<String, Object>> listaServicos(Map<String, Acumulador> dados) {
        List<Map<String, Object>> lista = new ArrayList<>();
        dados.forEach((nome, ac) -> {
            if (ac.realizados == 0) return;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nome", nome);
            m.put("quantidade", ac.realizados);
            m.put("faturamento", ac.faturamento);
            lista.add(m);
        });
        lista.sort(Comparator.comparing((Map<String, Object> m) -> (BigDecimal) m.get("faturamento")).reversed());
        return lista;
    }

    /** Série por dia quando o período é curto; agrupada por mês em períodos longos. */
    private List<Map<String, Object>> serie(
            Map<LocalDate, Acumulador> porDia, LocalDate inicio, LocalDate fim, long dias) {
        List<Map<String, Object>> lista = new ArrayList<>();
        if (dias <= 31) {
            for (LocalDate d = inicio; !d.isAfter(fim); d = d.plusDays(1)) {
                Acumulador ac = porDia.getOrDefault(d, new Acumulador());
                lista.add(ponto(d.toString(), ac));
            }
            return lista;
        }
        Map<String, Acumulador> porMes = new TreeMap<>();
        porDia.forEach((d, ac) -> porMes
                .computeIfAbsent(d.toString().substring(0, 7), k -> new Acumulador())
                .absorver(ac));
        porMes.forEach((mes, ac) -> lista.add(ponto(mes, ac)));
        return lista;
    }

    private Map<String, Object> ponto(String chave, Acumulador ac) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("chave", chave);
        m.put("atendimentos", ac.realizados);
        m.put("faturamento", ac.faturamento);
        m.put("previsto", ac.previsto);
        return m;
    }

    /** Slots que a barbearia tinha para vender no período, descontando bloqueios. */
    private int capacidadeSlots(Long barbeariaId, LocalDate inicio, LocalDate fim, int qtdBarbeiros) {
        if (qtdBarbeiros == 0) return 0;
        int slotsDia = agenda.gerarSlotsBase().size();
        int total = 0;
        for (LocalDate d = inicio; !d.isAfter(fim); d = d.plusDays(1)) {
            if (d.getDayOfWeek() == DayOfWeek.SUNDAY) continue;
            total += slotsDia * qtdBarbeiros;
        }
        for (BloqueioHorario b : bloqueios.findByBarbeariaIdAndDataBetween(barbeariaId, inicio, fim)) {
            total -= b.getBarbeiro() == null ? qtdBarbeiros : 1;
        }
        return Math.max(0, total);
    }

    private int blocosDe(Agendamento a) {
        int duracao = a.getServico().getDuracaoMin();
        return Math.max(1, (int) Math.ceil(duracao / (double) AgendaService.SLOT_MIN));
    }

    private BigDecimal valorDe(Agendamento a) {
        return a.getPrecoCobrado() != null ? a.getPrecoCobrado() : a.getServico().getPreco();
    }

    private Situacao situacao(Agendamento a, LocalDateTime agora) {
        return switch (a.getStatus()) {
            case CANCELADO -> Situacao.CANCELADO;
            case NAO_COMPARECEU -> Situacao.NAO_COMPARECEU;
            case FINALIZADO -> Situacao.REALIZADO;
            case CONFIRMADO -> LocalDateTime.of(a.getData(), a.getHoraFim()).isAfter(agora)
                    ? Situacao.PREVISTO
                    : Situacao.REALIZADO;
        };
    }

    private BigDecimal ticket(BigDecimal total, int qtd) {
        if (qtd == 0) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return total.divide(BigDecimal.valueOf(qtd), 2, RoundingMode.HALF_UP);
    }

    private double percentual(int parte, int total) {
        if (total <= 0) return 0d;
        return Math.round(parte * 1000d / total) / 10d;
    }

    private enum Situacao { REALIZADO, PREVISTO, NAO_COMPARECEU, CANCELADO }

    private static final class Acumulador {
        int realizados;
        int previstos;
        int noShows;
        int cancelados;
        int slotsOcupados;
        BigDecimal faturamento = BigDecimal.ZERO;
        BigDecimal previsto = BigDecimal.ZERO;
        BigDecimal perdido = BigDecimal.ZERO;

        void somar(Situacao s, BigDecimal valor, int blocos) {
            if (s != Situacao.CANCELADO) slotsOcupados += blocos;
            switch (s) {
                case REALIZADO -> {
                    realizados++;
                    faturamento = faturamento.add(valor);
                }
                case PREVISTO -> {
                    previstos++;
                    previsto = previsto.add(valor);
                }
                case NAO_COMPARECEU -> {
                    noShows++;
                    perdido = perdido.add(valor);
                }
                case CANCELADO -> cancelados++;
            }
        }

        void absorver(Acumulador o) {
            realizados += o.realizados;
            previstos += o.previstos;
            noShows += o.noShows;
            cancelados += o.cancelados;
            slotsOcupados += o.slotsOcupados;
            faturamento = faturamento.add(o.faturamento);
            previsto = previsto.add(o.previsto);
            perdido = perdido.add(o.perdido);
        }
    }
}
