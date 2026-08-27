package br.com.barberini.service;

import br.com.barberini.model.PlanoBarbearia;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Catálogo comercial Lâmina Pro (base R$ 49,90 · descontos 15%/30%). */
public final class CatalogoPlanos {

    private CatalogoPlanos() {}

    public record Item(
            String id,
            PlanoBarbearia faixa,
            String periodo,      // M | S | A
            String nome,
            String equipe,
            BigDecimal valor,
            String precoTexto,
            int dias,
            boolean recorrente,
            int maxBarbeiros,
            String dica
    ) {}

    private static final List<Item> ITENS = build();

    public static List<Map<String, Object>> publico() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Item i : ITENS) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", i.id());
            m.put("faixa", i.faixa().name());
            m.put("periodo", i.periodo());
            m.put("nome", i.nome());
            m.put("equipe", i.equipe());
            m.put("valor", i.valor());
            m.put("preco", i.precoTexto());
            m.put("dias", i.dias());
            m.put("recorrente", i.recorrente());
            m.put("maxBarbeiros", i.maxBarbeiros());
            m.put("dica", i.dica());
            out.add(m);
        }
        return out;
    }

    public static Item resolver(String planoId) {
        if (planoId == null || planoId.isBlank()) {
            return byId("p_1_2_m");
        }
        Item item = byId(planoId.trim().toLowerCase());
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plano inválido");
        }
        return item;
    }

    public static Item byId(String id) {
        for (Item i : ITENS) {
            if (i.id().equalsIgnoreCase(id)) return i;
        }
        return null;
    }

    public static int maxBarbeiros(PlanoBarbearia plano) {
        if (plano == null) return PlanoBarbearia.TRIAL.maxBarbeiros();
        return plano.maxBarbeiros();
    }

    private static List<Item> build() {
        List<Item> list = new ArrayList<>();
        addFaixa(list, PlanoBarbearia.P_1_2, "1 a 2 profissionais", 2,
                bd("49.90"), bd("254.49"), bd("419.16"));
        addFaixa(list, PlanoBarbearia.P_3_5, "3 a 5 profissionais", 5,
                bd("68.90"), bd("351.39"), bd("578.76"));
        addFaixa(list, PlanoBarbearia.P_6_15, "6 a 15 profissionais", 15,
                bd("102.90"), bd("524.79"), bd("864.36"));
        addFaixa(list, PlanoBarbearia.P_16_PLUS, "Mais de 15 profissionais", 999,
                bd("137.90"), bd("703.29"), bd("1158.36"));
        return List.copyOf(list);
    }

    private static void addFaixa(
            List<Item> list,
            PlanoBarbearia faixa,
            String equipe,
            int max,
            BigDecimal mensal,
            BigDecimal semestral,
            BigDecimal anual) {
        String code = switch (faixa) {
            case P_1_2 -> "1_2";
            case P_3_5 -> "3_5";
            case P_6_15 -> "6_15";
            case P_16_PLUS -> "16";
            default -> faixa.name().toLowerCase();
        };
        list.add(new Item(
                "p_" + code + "_m",
                faixa,
                "M",
                "Mensal · " + equipe,
                equipe,
                mensal,
                "R$ " + mensal.toPlainString().replace('.', ',') + "/mês",
                30,
                true,
                max,
                "Cartão com renovação automática todo mês."
        ));
        list.add(new Item(
                "p_" + code + "_s",
                faixa,
                "S",
                "Semestral · " + equipe,
                equipe,
                semestral,
                "R$ " + semestral.toPlainString().replace('.', ',') + " (−15%)",
                183,
                false,
                max,
                "Pagamento único (Pix ou cartão). 6 meses."
        ));
        list.add(new Item(
                "p_" + code + "_a",
                faixa,
                "A",
                "Anual · " + equipe,
                equipe,
                anual,
                "R$ " + anual.toPlainString().replace('.', ',') + " (−30%)",
                365,
                false,
                max,
                "Pagamento único (Pix ou cartão). 12 meses."
        ));
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
