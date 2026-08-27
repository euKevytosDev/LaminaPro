package br.com.barberini.service;

import br.com.barberini.model.Agendamento;
import br.com.barberini.model.Barbeiro;
import br.com.barberini.model.BloqueioHorario;
import br.com.barberini.model.StatusAgendamento;
import br.com.barberini.repository.AgendamentoRepository;
import br.com.barberini.repository.BarbeiroRepository;
import br.com.barberini.repository.BloqueioHorarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class AgendaService {

    public static final LocalTime ABERTURA = LocalTime.of(9, 0);
    public static final LocalTime FECHAMENTO = LocalTime.of(19, 0);
    public static final LocalTime ALMOCO_INI = LocalTime.of(12, 0);
    public static final LocalTime ALMOCO_FIM = LocalTime.of(13, 30);
    public static final int SLOT_MIN = 30;

    private final AgendamentoRepository agendamentos;
    private final BloqueioHorarioRepository bloqueios;
    private final BarbeiroRepository barbeiros;

    public AgendaService(
            AgendamentoRepository agendamentos,
            BloqueioHorarioRepository bloqueios,
            BarbeiroRepository barbeiros) {
        this.agendamentos = agendamentos;
        this.bloqueios = bloqueios;
        this.barbeiros = barbeiros;
    }

    public List<LocalTime> gerarSlotsBase() {
        List<LocalTime> slots = new ArrayList<>();
        LocalTime t = ABERTURA;
        while (!t.plusMinutes(SLOT_MIN).isAfter(FECHAMENTO)) {
            boolean almoco = !t.isBefore(ALMOCO_INI) && t.isBefore(ALMOCO_FIM);
            if (!almoco) slots.add(t);
            t = t.plusMinutes(SLOT_MIN);
        }
        return slots;
    }

    @Transactional(readOnly = true)
    public List<String> slotsDisponiveis(Long barbeiroId, LocalDate data, int duracaoMin) {
        if (data.getDayOfWeek() == DayOfWeek.SUNDAY) return List.of();

        Barbeiro barbeiro = barbeiros.findById(barbeiroId).orElse(null);
        if (barbeiro == null || barbeiro.getBarbearia() == null) return List.of();
        Long barbeariaId = barbeiro.getBarbearia().getId();

        Set<LocalTime> ocupados = new HashSet<>();
        for (Agendamento a : agendamentos.findOcupadosDoDia(
                barbeiroId, data, StatusAgendamento.CANCELADO)) {
            LocalTime cur = a.getHoraInicio();
            while (cur.isBefore(a.getHoraFim())) {
                ocupados.add(cur);
                cur = cur.plusMinutes(SLOT_MIN);
            }
        }
        for (BloqueioHorario b : bloqueios.findByBarbeariaIdAndDataAndBarbeiroIsNull(barbeariaId, data)) {
            ocupados.add(b.getHora());
        }
        for (BloqueioHorario b : bloqueios.findByBarbeariaIdAndDataAndBarbeiroId(barbeariaId, data, barbeiroId)) {
            ocupados.add(b.getHora());
        }

        List<LocalTime> base = gerarSlotsBase();
        int blocos = Math.max(1, (int) Math.ceil(duracaoMin / (double) SLOT_MIN));
        LocalTime agoraLimite = data.equals(LocalDate.now())
                ? LocalTime.now().plusMinutes(5)
                : LocalTime.MIDNIGHT;

        List<String> livres = new ArrayList<>();
        for (LocalTime inicio : base) {
            if (data.equals(LocalDate.now()) && !inicio.isAfter(agoraLimite)) continue;
            boolean ok = true;
            for (int i = 0; i < blocos; i++) {
                LocalTime h = inicio.plusMinutes((long) i * SLOT_MIN);
                if (!base.contains(h) || ocupados.contains(h)) {
                    ok = false;
                    break;
                }
            }
            if (ok) livres.add(inicio.toString().substring(0, 5));
        }
        return livres;
    }

    public LocalTime calcularFim(LocalTime inicio, int duracaoMin) {
        int blocos = Math.max(1, (int) Math.ceil(duracaoMin / (double) SLOT_MIN));
        return inicio.plusMinutes((long) blocos * SLOT_MIN);
    }
}
