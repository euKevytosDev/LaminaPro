package br.com.barberini.config;

import br.com.barberini.model.Barbearia;
import br.com.barberini.model.Barbeiro;
import br.com.barberini.model.Papel;
import br.com.barberini.model.Servico;
import br.com.barberini.model.Usuario;
import br.com.barberini.repository.BarbeariaRepository;
import br.com.barberini.repository.BarbeiroRepository;
import br.com.barberini.repository.ServicoRepository;
import br.com.barberini.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Configuration
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class SeedConfig {

    private static final Logger log = LoggerFactory.getLogger(SeedConfig.class);
    private static final int MAX_BARBEIROS_DEMO = 2;

    @Bean
    CommandLineRunner seed(
            UsuarioRepository usuarios,
            BarbeariaRepository barbearias,
            BarbeiroRepository barbeiros,
            ServicoRepository servicos,
            PasswordEncoder encoder,
            @Value("${app.seed.dono.email}") String donoEmail,
            @Value("${app.seed.dono.senha}") String donoSenha,
            @Value("${app.seed.dono.nome}") String donoNome) {
        return args -> {
            try {
                Barbearia demo = barbearias.findBySlugIgnoreCase("demo")
                        .orElseGet(() -> barbearias.save(new Barbearia("Barbearia Demo", "demo")));

                Usuario dono = usuarios.findByEmailIgnoreCase(donoEmail).orElse(null);
                if (dono == null) {
                    dono = new Usuario(donoEmail.toLowerCase(), donoNome, encoder.encode(donoSenha), Papel.DONO);
                    dono.setBarbearia(demo);
                    usuarios.save(dono);
                } else if (dono.getBarbearia() == null) {
                    dono.setBarbearia(demo);
                    if (dono.getPapel() != Papel.DONO) {
                        dono.setPapel(Papel.DONO);
                    }
                    usuarios.save(dono);
                }

                if (barbeiros.countByBarbeariaId(demo.getId()) == 0) {
                    barbeiros.save(new Barbeiro(demo, "Abner Barber", "AB", "#3d3d3d"));
                    barbeiros.save(new Barbeiro(demo, "Julio César", "JC", "#555555"));
                }
                normalizarBarbeirosDemo(demo.getId(), barbeiros);

                if (servicos.countByBarbeariaId(demo.getId()) == 0) {
                    Object[][] lista = {
                            {"Acabamento barba", "15", 15},
                            {"Acabamento cabelo", "15", 15},
                            {"Barboterapia", "35", 30},
                            {"Barboterapia + acabamento cabelo", "50", 45},
                            {"Barboterapia + sobrancelha", "50", 40},
                            {"Corte", "45", 30},
                            {"Corte + acabamento barba", "60", 40},
                            {"Corte + acabamento barba + sobrancelha", "75", 50},
                            {"Corte + Barboterapia", "80", 60},
                            {"Corte + Barboterapia + selagem", "170", 90},
                            {"Corte + selagem", "135", 75},
                            {"Corte + sobrancelha", "60", 40},
                            {"Corte + barboterapia + sobrancelha", "95", 70},
                            {"Limpeza de pele (contra oleosidade)", "20", 20},
                            {"Selagem", "90", 60},
                            {"Sobrancelha", "15", 15},
                            {"Tintura (A partir de)", "40", 45},
                    };
                    for (Object[] s : lista) {
                        servicos.save(new Servico(
                                demo,
                                (String) s[0],
                                new BigDecimal((String) s[1]),
                                (Integer) s[2]
                        ));
                    }
                }
            } catch (Exception e) {
                log.error("Seed falhou (API sobe mesmo assim): {}", e.getMessage());
            }
        };
    }

    /** Trial permite 2 barbeiros — mantém só os 2 primeiros ativos na demo. */
    private static void normalizarBarbeirosDemo(Long barbeariaId, BarbeiroRepository barbeiros) {
        List<Barbeiro> todos = barbeiros.findByBarbeariaIdOrderByNomeAsc(barbeariaId);
        todos.sort(Comparator.comparing(Barbeiro::getId));
        for (int i = 0; i < todos.size(); i++) {
            Barbeiro b = todos.get(i);
            boolean deveAtivar = i < MAX_BARBEIROS_DEMO;
            if (b.isAtivo() != deveAtivar) {
                b.setAtivo(deveAtivar);
                barbeiros.save(b);
                log.info("Demo barbeiro {} → ativo={}", b.getNome(), deveAtivar);
            }
        }
    }
}
