package br.com.barberini.model;

/** Faixa de equipe da loja (limite de barbeiros ativos). */
public enum PlanoBarbearia {
    TRIAL,
    /** Legado — equivale a P_1_2 */
    SOLO,
    P_1_2,
    P_3_5,
    P_6_15,
    P_16_PLUS;

    public int maxBarbeiros() {
        return switch (this) {
            case TRIAL, SOLO, P_1_2 -> 2;
            case P_3_5 -> 5;
            case P_6_15 -> 15;
            case P_16_PLUS -> 999;
        };
    }

    public PlanoBarbearia normalizado() {
        return this == SOLO ? P_1_2 : this;
    }

    public String rotulo() {
        return switch (normalizado()) {
            case TRIAL -> "Trial";
            case P_1_2 -> "1 a 2 profissionais";
            case P_3_5 -> "3 a 5 profissionais";
            case P_6_15 -> "6 a 15 profissionais";
            case P_16_PLUS -> "Mais de 15 profissionais";
            default -> name();
        };
    }
}
