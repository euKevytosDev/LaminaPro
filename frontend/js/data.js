/* Encaixe — produto, regras de agenda e utilitários */

window.ENCAIXE = {
  produto: {
    nome: "Encaixe",
    tagline: "Agenda que encaixa",
  },

  /** Preenchido dinamicamente pela loja (API) */
  estabelecimento: "ENCAIXE",
  nomeCompleto: "Encaixe",
  timezone: "America/Sao_Paulo",

  /** Cancelamento com menos de 1h: taxa 50% + bloqueio até pagar */
  politicaCancelamento: {
    antecedenciaMinutos: 60,
    taxaPercentual: 50,
    texto:
      "Cancelamentos devem ser feitos com pelo menos 1 hora de antecedência. " +
      "Caso contrário, poderá ser cobrada taxa de 50% e o agendamento futuro " +
      "fica bloqueado até o pagamento da taxa.",
  },

  /** Janela de agenda do cliente */
  janelaAgendaDias: 30,

  /** Slots de 30 minutos; intervalo de almoço 12:00–13:30 (defaults) */
  agenda: {
    slotMinutos: 30,
    abertura: "09:00",
    fechamento: "19:00",
    almocoInicio: "12:00",
    almocoFim: "13:30",
    diasFuncionamento: [1, 2, 3, 4, 5, 6], // seg–sáb (0=dom)
  },

  /** Catálogo vem da API da loja — sem fallback hardcoded */
  barbeiros: [],
  categorias: [{ id: "cabelo", nome: "CABELO" }],
  servicos: [],
};

window.ENCAIXE.utils = {
  dinheiro(v) {
    return Number(v || 0).toLocaleString("pt-BR", {
      style: "currency",
      currency: "BRL",
    });
  },

  pad2(n) {
    return String(n).padStart(2, "0");
  },

  /** "09:00" → minutos desde meia-noite */
  horaParaMinutos(hhmm) {
    const [h, m] = String(hhmm).split(":").map(Number);
    return h * 60 + m;
  },

  minutosParaHora(min) {
    const h = Math.floor(min / 60);
    const m = min % 60;
    return `${this.pad2(h)}:${this.pad2(m)}`;
  },

  /** Gera slots livres do dia (respeitando almoço) */
  gerarSlots() {
    const { abertura, fechamento, almocoInicio, almocoFim, slotMinutos } =
      window.ENCAIXE.agenda;
    const ini = this.horaParaMinutos(abertura);
    const fim = this.horaParaMinutos(fechamento);
    const aIni = this.horaParaMinutos(almocoInicio);
    const aFim = this.horaParaMinutos(almocoFim);
    const slots = [];
    for (let t = ini; t + slotMinutos <= fim; t += slotMinutos) {
      if (t >= aIni && t < aFim) continue;
      slots.push(this.minutosParaHora(t));
    }
    return slots;
  },

  /** Fim do atendimento a partir do início + duração do serviço */
  fimAtendimento(inicioHhmm, duracaoMin) {
    const slot = window.ENCAIXE.agenda.slotMinutos;
    const blocos = Math.max(1, Math.ceil(duracaoMin / slot));
    const fim = this.horaParaMinutos(inicioHhmm) + blocos * slot;
    return this.minutosParaHora(fim);
  },

  formatarDataBR(iso) {
    const [y, m, d] = iso.split("-");
    return `${d}/${m}/${y}`;
  },

  diaSemanaCurto(date) {
    return ["Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb"][date.getDay()];
  },

  diaSemanaLetra(date) {
    return ["D", "S", "T", "Q", "Q", "S", "S"][date.getDay()];
  },

  mesAno(date) {
    const meses = [
      "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
      "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro",
    ];
    return { mes: meses[date.getMonth()], ano: date.getFullYear() };
  },

  toISODate(date) {
    return `${date.getFullYear()}-${this.pad2(date.getMonth() + 1)}-${this.pad2(date.getDate())}`;
  },

  parseISODate(iso) {
    const [y, m, d] = iso.split("-").map(Number);
    return new Date(y, m - 1, d);
  },

  isMesmoDia(a, b) {
    return (
      a.getFullYear() === b.getFullYear() &&
      a.getMonth() === b.getMonth() &&
      a.getDate() === b.getDate()
    );
  },

  /** Slug amigável a partir do nome da barbearia */
  slugify(texto) {
    return String(texto || "")
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, "-")
      .replace(/^-+|-+$/g, "")
      .slice(0, 60);
  },

  /** Comprime imagem (file/blob) para JPEG data URL ~max 400px / quality 0.7 */
  async comprimirImagem(file, maxPx = 400, quality = 0.7) {
    if (!file) return null;
    const bitmap = await createImageBitmap(file);
    const scale = Math.min(1, maxPx / Math.max(bitmap.width, bitmap.height));
    const w = Math.max(1, Math.round(bitmap.width * scale));
    const h = Math.max(1, Math.round(bitmap.height * scale));
    const canvas = document.createElement("canvas");
    canvas.width = w;
    canvas.height = h;
    const ctx = canvas.getContext("2d");
    ctx.drawImage(bitmap, 0, 0, w, h);
    bitmap.close?.();
    return canvas.toDataURL("image/jpeg", quality);
  },
};

/** Alias legado — código antigo que ainda referencia BARBERINI */
window.BARBERINI = window.ENCAIXE;
