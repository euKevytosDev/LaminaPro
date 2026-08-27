/* Persistência local (por slug) + cache de catálogo + rascunho + APIs do dono */

window.Store = (() => {
  const KEY_SLUG = "laminapro_slug";
  const KEY_USER = "laminapro_user";
  const KEY_LOJA = "laminapro_loja_meta";

  /** Migra chaves antigas Encaixe/Barberini → Lâmina Pro (uma vez) */
  (function migrarKeys() {
    const pares = [
      ["encaixe_slug", KEY_SLUG],
      ["barberini_slug", KEY_SLUG],
      ["encaixe_user", KEY_USER],
      ["barberini_user", KEY_USER],
      ["encaixe_loja_meta", KEY_LOJA],
      ["barberini_loja_meta", KEY_LOJA],
    ];
    for (const [de, para] of pares) {
      if (!localStorage.getItem(para) && localStorage.getItem(de)) {
        localStorage.setItem(para, localStorage.getItem(de));
      }
    }
  })();

  let currentSlug = localStorage.getItem(KEY_SLUG) || "";
  let lojaMeta = null;

  try {
    lojaMeta = JSON.parse(localStorage.getItem(KEY_LOJA) || "null");
  } catch {
    lojaMeta = null;
  }

  function keyState() {
    const s = currentSlug || "_app";
    return `laminapro_${s}_v1`;
  }
  function keyDraft() {
    return `laminapro_${currentSlug || "_app"}_draft`;
  }
  function keyCatalog() {
    return `laminapro_${currentSlug || "_app"}_catalog`;
  }
  function keySlots() {
    return `laminapro_${currentSlug || "_app"}_slots`;
  }

  const padrao = () => ({
    usuario: null,
    agendamentos: [],
  });

  function lerUsuarioGlobal() {
    try {
      const raw = localStorage.getItem(KEY_USER);
      if (!raw) return null;
      return JSON.parse(raw);
    } catch {
      return null;
    }
  }

  function salvarUsuarioGlobal(usuario) {
    if (usuario) localStorage.setItem(KEY_USER, JSON.stringify(usuario));
    else localStorage.removeItem(KEY_USER);
  }

  function ler() {
    try {
      const raw = localStorage.getItem(keyState());
      const base = padrao();
      base.usuario = lerUsuarioGlobal();
      if (!raw) return base;
      const parsed = JSON.parse(raw);
      return {
        ...base,
        ...parsed,
        usuario: lerUsuarioGlobal() || parsed.usuario || null,
      };
    } catch {
      return { ...padrao(), usuario: lerUsuarioGlobal() };
    }
  }

  function salvar(state) {
    const { usuario, ...rest } = state;
    if (usuario !== undefined) salvarUsuarioGlobal(usuario);
    localStorage.setItem(
      keyState(),
      JSON.stringify({ ...rest, agendamentos: rest.agendamentos || [] })
    );
  }

  function atualizar(fn) {
    const state = ler();
    const next = fn(state) || state;
    salvar(next);
    return next;
  }

  function aplicarBrandingLoja(loja) {
    if (!loja) return;
    lojaMeta = {
      id: loja.id,
      nome: loja.nome,
      slug: loja.slug,
      telefone: loja.telefone || null,
      logoData: loja.logoData || null,
      plano: loja.plano || null,
      statusAssinatura: loja.statusAssinatura || null,
    };
    localStorage.setItem(KEY_LOJA, JSON.stringify(lojaMeta));
    const E = window.ENCAIXE;
    if (E) {
      E.estabelecimento = (loja.nome || "Lâmina Pro").toUpperCase();
      E.nomeCompleto = loja.nome || "Lâmina Pro";
    }
  }

  function normServico(s) {
    return {
      id: Number(s.id),
      nome: s.nome,
      preco: Number(s.preco),
      duracaoMin: Number(s.duracaoMin ?? s.duracao ?? 30),
      ativo: s.ativo !== false,
    };
  }

  function normBarbeiro(b) {
    return {
      id: Number(b.id),
      nome: b.nome,
      iniciais: b.iniciais,
      cor: b.cor || "#3d3d3d",
      ativo: b.ativo !== false,
      fotoData: b.fotoData || null,
    };
  }

  function fallbackCatalogo() {
    return {
      barbeiros: [],
      servicos: [],
      offline: true,
      atualizadoEm: null,
    };
  }

  function lerCatalogo() {
    try {
      const raw = localStorage.getItem(keyCatalog());
      if (!raw) return null;
      const c = JSON.parse(raw);
      return {
        barbeiros: (c.barbeiros || []).map(normBarbeiro),
        servicos: (c.servicos || []).map(normServico),
        offline: !!c.offline,
        atualizadoEm: c.atualizadoEm || null,
      };
    } catch {
      return null;
    }
  }

  function salvarCatalogo(barbeiros, servicos, offline = false) {
    const payload = {
      barbeiros: barbeiros.map(normBarbeiro),
      servicos: servicos.map(normServico),
      offline,
      atualizadoEm: new Date().toISOString(),
    };
    localStorage.setItem(keyCatalog(), JSON.stringify(payload));
    return payload;
  }

  function catalogoAtual() {
    const cached = lerCatalogo();
    if (cached) return cached;
    return fallbackCatalogo();
  }

  function slotsCacheKey(barbeiroId, data, servicoId) {
    return `${barbeiroId}|${data}|${servicoId || ""}`;
  }

  function lerSlotsCache(key) {
    try {
      const all = JSON.parse(localStorage.getItem(keySlots()) || "{}");
      return all[key] || null;
    } catch {
      return null;
    }
  }

  function salvarSlotsCache(key, slots) {
    try {
      const all = JSON.parse(localStorage.getItem(keySlots()) || "{}");
      all[key] = { slots, em: Date.now() };
      const keys = Object.keys(all);
      if (keys.length > 80) {
        keys.sort((a, b) => (all[a].em || 0) - (all[b].em || 0));
        keys.slice(0, keys.length - 60).forEach((k) => delete all[k]);
      }
      localStorage.setItem(keySlots(), JSON.stringify(all));
    } catch {
      /* ignore */
    }
  }

  function limparSlotsCache(barbeiroId, data) {
    try {
      if (barbeiroId == null && !data) {
        localStorage.removeItem(keySlots());
        return;
      }
      const all = JSON.parse(localStorage.getItem(keySlots()) || "{}");
      Object.keys(all).forEach((k) => {
        const [bid, d] = k.split("|");
        if (barbeiroId != null && Number(bid) !== Number(barbeiroId)) return;
        if (data && d !== data) return;
        delete all[k];
      });
      localStorage.setItem(keySlots(), JSON.stringify(all));
    } catch {
      /* ignore */
    }
  }

  function slotsFallback(barbeiroId, dataIso, duracaoMin, bloqueios = []) {
    const U = window.ENCAIXE.utils;
    const B = window.ENCAIXE;
    const todos = U.gerarSlots();
    const ocupados = new Set(
      ler()
        .agendamentos.filter(
          (a) => Number(a.barbeiroId) === Number(barbeiroId) && a.data === dataIso
        )
        .map((a) => (a.hora || "").substring(0, 5))
    );
    (bloqueios || []).forEach((bl) => {
      const doBarbeiro =
        bl.barbeiroId == null || Number(bl.barbeiroId) === Number(barbeiroId);
      if (doBarbeiro) ocupados.add(String(bl.hora || "").substring(0, 5));
    });
    const hojeIso = U.toISODate(new Date());
    const agoraMin =
      dataIso === hojeIso
        ? new Date().getHours() * 60 + new Date().getMinutes()
        : -1;
    const blocos = Math.max(1, Math.ceil(duracaoMin / B.agenda.slotMinutos));

    return todos.filter((hora) => {
      const ini = U.horaParaMinutos(hora);
      if (agoraMin >= 0 && ini <= agoraMin) return false;
      for (let i = 0; i < blocos; i++) {
        const h = U.minutosParaHora(ini + i * B.agenda.slotMinutos);
        if (ocupados.has(h)) return false;
        if (!todos.includes(h) && i > 0) return false;
      }
      return true;
    });
  }

  function exigirSlug() {
    if (!currentSlug) {
      const err = new Error("Informe a loja (slug) para continuar");
      err.semSlug = true;
      throw err;
    }
    return currentSlug;
  }

  function aplicarUsuarioAuth(data) {
    window.API.setToken(data.token);
    const usuario = { ...data.usuario, demo: false };
    atualizar((s) => {
      s.usuario = usuario;
      return s;
    });
    if (usuario.slug) {
      // dono: já sabe a loja
      if (!currentSlug) {
        currentSlug = usuario.slug;
        localStorage.setItem(KEY_SLUG, currentSlug);
      }
      if (usuario.nomeBarbearia) {
        aplicarBrandingLoja({
          nome: usuario.nomeBarbearia,
          slug: usuario.slug,
          id: usuario.barbeariaId,
        });
      }
    }
    return usuario;
  }

  return {
    setLoja(slug) {
      const s = String(slug || "")
        .trim()
        .toLowerCase();
      if (!s) return currentSlug;
      currentSlug = s;
      localStorage.setItem(KEY_SLUG, currentSlug);
      return currentSlug;
    },

    getSlug() {
      return currentSlug || "";
    },

    clearLoja() {
      currentSlug = "";
      localStorage.removeItem(KEY_SLUG);
      lojaMeta = null;
      localStorage.removeItem(KEY_LOJA);
      const E = window.ENCAIXE;
      if (E) {
        E.estabelecimento = "LÂMINA PRO";
        E.nomeCompleto = "Lâmina Pro";
      }
    },

    getLoja() {
      return lojaMeta;
    },

    setLojaMeta(loja) {
      aplicarBrandingLoja(loja);
      return lojaMeta;
    },

    get() {
      return ler();
    },

    getUsuario() {
      return lerUsuarioGlobal() || ler().usuario;
    },

    isDono() {
      const u = this.getUsuario();
      return u && u.papel === "DONO";
    },

    temAuthReal() {
      return !!window.API.token();
    },

    async loginApi(email, senha) {
      const data = await window.API.post("/api/auth/login", { email, senha });
      return aplicarUsuarioAuth(data);
    },

    async loginGoogle(credential) {
      const data = await window.API.post("/api/auth/google", { credential });
      return aplicarUsuarioAuth(data);
    },

    async cadastroApi(nome, email, senha) {
      const data = await window.API.post("/api/auth/cadastro", { nome, email, senha });
      return aplicarUsuarioAuth(data);
    },

    async criarLoja({ nome, email, senha, nomeBarbearia, slug, telefone }) {
      const data = await window.API.post("/api/auth/criar-loja", {
        nome,
        email,
        senha,
        nomeBarbearia,
        slug: slug || undefined,
        telefone: telefone || undefined,
      });
      const usuario = aplicarUsuarioAuth(data);
      if (usuario.slug) this.setLoja(usuario.slug);
      if (usuario.nomeBarbearia) {
        aplicarBrandingLoja({
          id: usuario.barbeariaId,
          nome: usuario.nomeBarbearia,
          slug: usuario.slug,
          telefone: telefone || null,
        });
      }
      return usuario;
    },

    loginDemo(nome, email) {
      const usuario = {
        id: null,
        nome: (nome || "Cliente").trim(),
        email: (email || "demo@local").trim().toLowerCase(),
        papel: "CLIENTE",
        demo: true,
      };
      window.API.setToken(null);
      salvarUsuarioGlobal(usuario);
      return usuario;
    },

    logout() {
      window.API.setToken(null);
      salvarUsuarioGlobal(null);
      return atualizar((s) => {
        s.usuario = null;
        return s;
      });
    },

    getDraft() {
      try {
        const raw = localStorage.getItem(keyDraft());
        if (!raw) return null;
        return JSON.parse(raw);
      } catch {
        return null;
      }
    },

    saveDraft(draft) {
      localStorage.setItem(keyDraft(), JSON.stringify(draft));
    },

    clearDraft() {
      localStorage.removeItem(keyDraft());
    },

    async carregarLojaPublica(slug) {
      const s = slug || exigirSlug();
      this.setLoja(s);
      const loja = await window.API.get(`/api/public/${encodeURIComponent(s)}`);
      aplicarBrandingLoja(loja);
      return loja;
    },

    async syncCatalogo() {
      const slug = exigirSlug();
      try {
        const [barbeiros, servicos] = await Promise.all([
          window.API.get(`/api/public/${encodeURIComponent(slug)}/barbeiros`),
          window.API.get(`/api/public/${encodeURIComponent(slug)}/servicos`),
        ]);
        return salvarCatalogo(barbeiros, servicos, false);
      } catch (e) {
        const cached = lerCatalogo();
        if (cached && (cached.barbeiros.length || cached.servicos.length)) {
          return { ...cached, offline: true };
        }
        return salvarCatalogo([], [], true);
      }
    },

    getBarbeiros(soAtivos = true) {
      const list = catalogoAtual().barbeiros;
      return soAtivos ? list.filter((b) => b.ativo) : list;
    },

    getServicos(soAtivos = true) {
      const list = catalogoAtual().servicos;
      return soAtivos ? list.filter((s) => s.ativo) : list;
    },

    getBarbeiro(id) {
      return this.getBarbeiros(false).find((b) => Number(b.id) === Number(id));
    },

    getServico(id) {
      return this.getServicos(false).find((s) => Number(s.id) === Number(id));
    },

    catalogoOffline() {
      return catalogoAtual().offline === true;
    },

    async fetchSlots(barbeiroId, data, servicoId) {
      const slug = exigirSlug();
      const serv = servicoId ? this.getServico(servicoId) : null;
      const duracaoMin = serv?.duracaoMin || 30;
      const key = slotsCacheKey(barbeiroId, data, servicoId);

      try {
        const q = new URLSearchParams({
          barbeiroId: String(barbeiroId),
          data,
        });
        if (servicoId) q.set("servicoId", String(servicoId));
        q.set("_", String(Date.now()));
        const res = await window.API.get(
          `/api/public/${encodeURIComponent(slug)}/slots?${q}`
        );
        const slots = (res.slots || []).map((h) => String(h).substring(0, 5));
        salvarSlotsCache(key, slots);
        return slots;
      } catch (e) {
        const cached = lerSlotsCache(key);
        if (cached && cached.slots && Date.now() - (cached.em || 0) < 10000) {
          return cached.slots;
        }
        let bloqueios = [];
        try {
          bloqueios = await window.API.get(
            `/api/public/${encodeURIComponent(slug)}/bloqueios?data=${encodeURIComponent(data)}`
          );
        } catch {
          /* offline total */
        }
        return slotsFallback(barbeiroId, data, duracaoMin, bloqueios);
      }
    },

    normAgendamento(ag) {
      return {
        id: ag.id,
        servicoId: Number(ag.servicoId),
        barbeiroId: Number(ag.barbeiroId),
        data: ag.data,
        hora: (ag.hora || ag.horaInicio || "").substring(0, 5),
        fim: (ag.fim || ag.horaFim || "").substring(0, 5),
        observacao: ag.observacao || "",
        servicoNome: ag.servicoNome,
        barbeiroNome: ag.barbeiroNome,
        barbeiroIniciais: ag.barbeiroIniciais,
        barbeiroCor: ag.barbeiroCor,
        clienteNome: ag.clienteNome,
        criadoEm: ag.criadoEm || new Date().toISOString(),
        status: ag.status || "CONFIRMADO",
        semPreferencia: !!ag.semPreferencia,
        valorCobrado: ag.valorCobrado != null ? Number(ag.valorCobrado) : null,
        formaPagamento: ag.formaPagamento || null,
        slug: ag.slug || currentSlug || null,
      };
    },

    addAgendamento(ag) {
      const norm = this.normAgendamento(ag);
      return atualizar((s) => {
        const exists = s.agendamentos.some((a) => Number(a.id) === Number(norm.id));
        if (!exists) s.agendamentos.push(norm);
        return s;
      });
    },

    removeAgendamentoLocal(id) {
      return atualizar((s) => {
        s.agendamentos = s.agendamentos.filter((a) => String(a.id) !== String(id));
        return s;
      });
    },

    async cancelarAgendamento(id) {
      if (window.API.token()) {
        await window.API.del(`/api/agendamentos/${id}`);
      }
      return this.removeAgendamentoLocal(id);
    },

    async syncMeusAgendamentos() {
      if (!window.API.token()) return ler().agendamentos;
      try {
        const lista = await window.API.get("/api/agendamentos/meus");
        const norm = lista.map((a) => this.normAgendamento(a));
        return atualizar((s) => {
          const ids = new Set(norm.map((a) => String(a.id)));
          const locais = s.agendamentos.filter((a) => !ids.has(String(a.id)));
          s.agendamentos = [...norm, ...locais].sort((a, b) =>
            (a.data + a.hora).localeCompare(b.data + b.hora)
          );
          return s;
        }).agendamentos;
      } catch {
        return ler().agendamentos;
      }
    },

    proximos(dias) {
      const hoje = new Date();
      hoje.setHours(0, 0, 0, 0);
      const limite = new Date(hoje);
      limite.setDate(limite.getDate() + dias);
      return ler()
        .agendamentos.filter((a) => {
          if (a.status === "CANCELADO") return false;
          const d = window.ENCAIXE.utils.parseISODate(a.data);
          return d >= hoje && d <= limite;
        })
        .sort((a, b) => (a.data + a.hora).localeCompare(b.data + b.hora));
    },

    /* --- Painel do dono --- */

    async donoAgendamentos(dias = 30, diasAtras = 7) {
      return window.API.get(
        `/api/dono/agendamentos?dias=${dias}&diasAtras=${diasAtras}`
      );
    },

    async donoReatribuirBarbeiro(id, barbeiroId) {
      return window.API.put(`/api/dono/agendamentos/${id}/barbeiro`, {
        barbeiroId: Number(barbeiroId),
      });
    },

    async donoAtualizarStatus(id, status, valorCobrado, formaPagamento) {
      const body = { status };
      if (valorCobrado != null && valorCobrado !== "") {
        body.valorCobrado = Number(valorCobrado);
      }
      if (formaPagamento) {
        body.formaPagamento = formaPagamento;
      }
      return window.API.put(`/api/dono/agendamentos/${id}/status`, body);
    },

    async donoResumo(inicio, fim) {
      return window.API.get(`/api/dono/resumo?inicio=${inicio}&fim=${fim}`);
    },

    async donoAssinaturaStatus() {
      return window.API.get("/api/dono/assinatura/status");
    },

    async donoAssinaturaCatalogo() {
      return window.API.get("/api/dono/assinatura/catalogo");
    },

    async donoCheckoutAssinatura(planoId) {
      return window.API.post("/api/dono/assinatura/checkout", { planoId: planoId || "p_1_2_m" });
    },

    async donoBarbeiros() {
      return window.API.get("/api/dono/barbeiros");
    },

    async donoCriarBarbeiro(body) {
      return window.API.post("/api/dono/barbeiros", body);
    },

    async donoAtualizarBarbeiro(id, body) {
      return window.API.put(`/api/dono/barbeiros/${id}`, body);
    },

    async donoServicos() {
      return window.API.get("/api/dono/servicos");
    },

    async donoCriarServico(body) {
      return window.API.post("/api/dono/servicos", body);
    },

    async donoAtualizarServico(id, body) {
      return window.API.put(`/api/dono/servicos/${id}`, body);
    },

    async donoBloqueios(data) {
      return window.API.get(`/api/dono/bloqueios?data=${data}`);
    },

    async donoCriarBloqueio(body) {
      const res = await window.API.post("/api/dono/bloqueios", body);
      limparSlotsCache(body.barbeiroId, body.data);
      return res;
    },

    async donoRemoverBloqueio(id, meta = {}) {
      const res = await window.API.del(`/api/dono/bloqueios/${id}`);
      limparSlotsCache(meta.barbeiroId, meta.data);
      return res;
    },

    /**
     * Sync em lote: { data, barbeiroId, bloqueios:[{hora,motivo}], removerIds:[...] }
     */
    async donoSyncBloqueios(payload) {
      const res = await window.API.post("/api/dono/bloqueios/sync", payload);
      limparSlotsCache(payload.barbeiroId, payload.data);
      return res;
    },

    async donoLoja() {
      const loja = await window.API.get("/api/dono/loja");
      aplicarBrandingLoja(loja);
      if (loja.slug) this.setLoja(loja.slug);
      return loja;
    },

    async donoAtualizarLoja(body) {
      const loja = await window.API.put("/api/dono/loja", body);
      aplicarBrandingLoja(loja);
      return loja;
    },
  };
})();
