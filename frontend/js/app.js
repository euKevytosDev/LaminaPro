/* Lâmina Pro — app web multi-tenant, mobile-first, sync na confirmação */

(() => {
  const U = () => window.ENCAIXE.utils;
  const B = () => window.ENCAIXE;
  const P = () => window.ENCAIXE.produto;
  const BRAND_MARK = (size = 72) =>
    `<img src="assets/laminapro-mark.png" alt="" width="${size}" height="${size}" />`;
  const BRAND_MARK_MINI = BRAND_MARK(36);

  const booking = {
    path: null,
    servicoId: null,
    barbeiroId: null,
    data: null,
    hora: null,
    observacao: "",
    semPreferencia: false,
  };

  let calCursor = new Date();
  let dataSelecionada = new Date();
  let ultimoAgendamento = null;
  let painelSecao = "resumo";

  let resumoPeriodo = "hoje";
  let resumoInicio = null;
  let resumoFim = null;
  let agendaFiltro = "hoje";

  /** Contexto de rota: entry | criar-loja | login | loja | dono */
  let rotaAtual = { tipo: "entry", slug: null };

  /** Painel: horários (draft local + sync no salvar) */
  let bloqueioBarbeiroId = null;
  let bloqueioDataSel = new Date();
  let bloqueioCalCursor = new Date();
  /** Map hora → { id?, hora } do servidor (baseline) */
  let bloqueiosServerMap = new Map();
  /** Set de horas bloqueadas no draft local */
  let bloqueiosDraftSet = new Set();
  let bloqueiosDirty = false;
  let bloqueiosOcupados = new Set();
  let bloqueiosSlugDraftKey = "";

  /** Lembretes browser — desativado; cliente usa Google Agenda */
  let lembreteTimers = [];
  let lembreteIds = new Set();
  let notifPedida = false;

  const GOOGLE_CLIENT_ID =
    "868389533637-d3l4a0mrnnbf7i1h34cd0mts996sb6pc.apps.googleusercontent.com";
  let googlePronto = false;
  let slugAutoEditado = true;

  const $ = (sel, root = document) => root.querySelector(sel);
  const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

  function show(el) {
    if (el) el.classList.remove("oculto");
  }
  function hide(el) {
    if (el) el.classList.add("oculto");
  }

  function toast(msg) {
    let t = $("#toast");
    if (!t) {
      t = document.createElement("div");
      t.id = "toast";
      t.className = "toast";
      document.body.appendChild(t);
    }
    t.textContent = msg;
    t.classList.add("visivel");
    clearTimeout(t._timer);
    t._timer = setTimeout(() => t.classList.remove("visivel"), 2800);
  }

  function nomeLojaDisplay() {
    const loja = Store.getLoja();
    if (loja?.nome) return loja.nome.toUpperCase();
    if (rotaAtual.tipo === "dono") return P().nome.toUpperCase();
    return (B().estabelecimento || P().nome).toUpperCase();
  }

  function letraMarca() {
    const n = nomeLojaDisplay();
    return (n && n[0]) || "L";
  }

  function atualizarMarcasUI() {
    const nome = nomeLojaDisplay();
    const letra = letraMarca();
    const header = $("#header-marca");
    if (header) header.textContent = rotaAtual.tipo === "dono" ? P().nomeCurto : nome;
    const mini = $("#logo-mini");
    if (mini) {
      const loja = Store.getLoja();
      if (loja?.logoData && rotaAtual.tipo !== "dono") {
        mini.innerHTML = `<img src="${loja.logoData}" alt="" />`;
        mini.classList.add("com-logo");
      } else if (rotaAtual.tipo === "dono") {
        mini.innerHTML = BRAND_MARK_MINI;
        mini.classList.add("com-logo", "brand-mark-mini");
      } else {
        mini.textContent = letra;
        mini.classList.remove("com-logo");
      }
    }
    const vazia = $("#agenda-vazia-marca");
    if (vazia) vazia.textContent = nome;
    $$(".marca-dinamica").forEach((el) => {
      el.textContent = nome;
    });
  }

  function avatarHtml(pessoa, fallbackIni = "?") {
    const cor = pessoa?.cor || "#333";
    const ini = pessoa?.iniciais || fallbackIni;
    if (pessoa?.fotoData) {
      return `<div class="avatar avatar-foto" style="background:${cor}"><img src="${pessoa.fotoData}" alt="" /></div>`;
    }
    return `<div class="avatar" style="background:${cor}">${ini}</div>`;
  }

  function salvarDraft() {
    Store.saveDraft({ ...booking });
  }

  function restaurarDraft() {
    const d = Store.getDraft();
    if (!d) return;
    Object.assign(booking, d);
  }

  function resetBooking() {
    booking.path = null;
    booking.servicoId = null;
    booking.barbeiroId = null;
    booking.data = null;
    booking.hora = null;
    booking.observacao = "";
    booking.semPreferencia = false;
    dataSelecionada = new Date();
    calCursor = new Date();
    Store.clearDraft();
  }

  function horaApi(hhmm) {
    const h = (hhmm || "").substring(0, 5);
    return h.length === 5 ? h + ":00" : h;
  }

  function googleCalendarUrl(ag) {
    const serv = Store.getServico(ag.servicoId);
    const barb = Store.getBarbeiro(ag.barbeiroId);
    const titulo = encodeURIComponent(
      `${serv?.nome || "Serviço"} — ${B().estabelecimento}`
    );
    const detalhes = encodeURIComponent(
      `Barbeiro: ${barb?.nome || ag.barbeiroNome || ""}\n` +
        (ag.observacao ? `Obs: ${ag.observacao}\n` : "") +
        B().politicaCancelamento.texto
    );
    const [y, m, d] = ag.data.split("-");
    const [hi, mi] = ag.hora.substring(0, 5).split(":");
    const fim = ag.fim || U().fimAtendimento(ag.hora, serv?.duracaoMin || 30);
    const [hf, mf] = fim.substring(0, 5).split(":");
    const start = `${y}${m}${d}T${hi}${mi}00`;
    const end = `${y}${m}${d}T${hf}${mf}00`;
    return (
      "https://calendar.google.com/calendar/render?action=TEMPLATE" +
      `&text=${titulo}&dates=${start}/${end}&details=${detalhes}`
    );
  }

  function abrirGoogleAgenda(ag) {
    if (!ag) return;
    window.open(googleCalendarUrl(ag), "_blank", "noopener,noreferrer");
  }

  /* ---------- routing (hash) ---------- */

  function parseHash() {
    const raw = (location.hash || "").replace(/^#/, "");
    const [pathOnly, query] = raw.split("?");
    const path = (pathOnly || "").replace(/^\//, "");
    const params = new URLSearchParams(query || "");
    if (!path || path === "/") return { tipo: "entry", slug: null };
    const parts = path.split("/").filter(Boolean);
    if (parts[0] === "loja" && parts[1]) {
      return { tipo: "loja", slug: parts[1].toLowerCase() };
    }
    if (parts[0] === "criar-loja") return { tipo: "criar-loja", slug: null };
    if (parts[0] === "login") {
      return {
        tipo: "login",
        slug: Store.getSlug() || null,
        intent: params.get("como") === "dono" ? "dono" : null,
      };
    }
    if (parts[0] === "dono") {
      return { tipo: "dono", slug: null, pago: params.get("pago") };
    }
    return { tipo: "entry", slug: null };
  }

  function setHash(path) {
    const next = path.startsWith("#") ? path : `#${path}`;
    if (location.hash === next) {
      onHashChange();
      return;
    }
    location.hash = next;
  }

  function esconderTelas() {
    hide($("#tela-entry"));
    hide($("#tela-criar-loja"));
    hide($("#tela-login"));
    hide($("#tela-app"));
  }

  function mostrarEntry() {
    esconderTelas();
    show($("#tela-entry"));
    rotaAtual = { tipo: "entry", slug: null };
  }

  function mostrarCriarLoja() {
    esconderTelas();
    show($("#tela-criar-loja"));
    rotaAtual = { tipo: "criar-loja", slug: null };
    slugAutoEditado = true;
    atualizarSlugPreview();
  }

  function mostrarLogin(ctx = {}) {
    esconderTelas();
    show($("#tela-login"));
    rotaAtual = { tipo: "login", slug: ctx.slug || Store.getSlug() || null, intent: ctx.intent || null };
    const loja = Store.getLoja();
    const titulo = $("#login-marca-titulo");
    const sub = $("#login-marca-sub");
    const letra = $("#login-logo-letra");
    const authTitulo = $("#auth-titulo");

    if (ctx.intent === "dono") {
      if (titulo) titulo.textContent = P().nomeCurto;
      if (sub) sub.textContent = "Acesso do dono / admin";
      if (letra) {
        letra.className = "brand-mark brand-mark-lg";
        letra.innerHTML = BRAND_MARK(72);
      }
      if (authTitulo) authTitulo.textContent = "Entrar como dono";
      $("#btn-auth-cadastro")?.classList.add("oculto");
      alternarModoAuth(false);
    } else if (rotaAtual.slug && loja?.nome) {
      if (titulo) titulo.textContent = loja.nome.toUpperCase();
      if (sub) sub.textContent = "Agende seu horário";
      if (letra) {
        if (loja.logoData) {
          letra.className = "brand-mark brand-mark-lg";
          letra.innerHTML = `<img src="${loja.logoData}" alt="" width="72" height="72" />`;
        } else {
          letra.className = "login-b";
          letra.textContent = (loja.nome[0] || "L").toUpperCase();
        }
      }
      $("#btn-auth-cadastro")?.classList.remove("oculto");
    } else {
      if (titulo) titulo.textContent = P().nomeCurto;
      if (sub) sub.textContent = P().tagline;
      if (letra) {
        letra.className = "brand-mark brand-mark-lg";
        letra.innerHTML = BRAND_MARK(72);
      }
      $("#btn-auth-cadastro")?.classList.remove("oculto");
    }
    initGoogleSignIn();
  }

  async function entrarApp({ modo = null, tab = null } = {}) {
    const u = usuarioLogado();
    const slug = Store.getSlug();
    const modoFinal =
      modo ||
      (u?.papel === "DONO" && !slug ? "dono" : slug ? "loja" : u?.papel === "DONO" ? "dono" : "loja");

    rotaAtual = {
      tipo: modoFinal,
      slug: slug || u?.slug || null,
    };

    if (modoFinal === "dono" && u?.slug) {
      Store.setLoja(u.slug);
      try {
        await Store.donoLoja();
      } catch {
        /* ok */
      }
    }

    esconderTelas();
    show($("#tela-app"));
    atualizarMarcasUI();
    atualizarHeaderUsuario();
    atualizarNavDono();

    if (Store.getSlug()) {
      try {
        await Store.syncCatalogo();
      } catch {
        /* offline */
      }
    }

    if (Store.temAuthReal()) {
      try {
        await Store.syncMeusAgendamentos();
      } catch {
        /* local ok */
      }
    }

    let tabInicial = tab || (modoFinal === "dono" ? "painel" : "agenda");
    if (modoFinal === "dono") {
      try {
        const st = await Store.donoAssinaturaStatus();
        aplicarPaywallDono(st);
        if (st && st.bloqueada) {
          painelSecao = "planos";
          tabInicial = "painel";
        }
      } catch {
        esconderPaywallDono();
      }
    } else {
      esconderPaywallDono();
    }

    renderAgenda();
    ativarTab(tabInicial);
  }

  async function entrarLoja(slug) {
    const s = String(slug || "")
      .trim()
      .toLowerCase();
    if (!s) {
      toast("Informe o link da loja");
      return;
    }
    try {
      Store.setLoja(s);
      await Store.carregarLojaPublica(s);
      setHash(`/loja/${s}`);
      await entrarApp({ modo: "loja", tab: "agenda" });
    } catch (e) {
      toast(e.message || "Loja não encontrada");
      mostrarEntry();
    }
  }

  async function onHashChange() {
    const rota = parseHash();
    rotaAtual = rota;

    if (rota.tipo === "criar-loja") {
      mostrarCriarLoja();
      return;
    }

    if (rota.tipo === "loja") {
      try {
        Store.setLoja(rota.slug);
        Store.setUltimoModo("cliente");
        await Store.carregarLojaPublica(rota.slug);
        await entrarApp({ modo: "loja" });
      } catch (e) {
        toast(e.message || "Loja não encontrada");
        setHash("/");
      }
      return;
    }

    if (rota.tipo === "login") {
      const u = await garantirSessao();
      if (u && Store.temAuthReal() && !u.demo) {
        if (rota.intent === "dono" && u.papel !== "DONO") {
          mostrarLogin({ slug: Store.getSlug(), intent: rota.intent });
          return;
        }
        await aposAuth(u, true);
        return;
      }
      mostrarLogin({ slug: Store.getSlug(), intent: rota.intent });
      return;
    }

    if (rota.tipo === "dono") {
      if (rota.pago === "ok") toast("Pagamento ok! A assinatura confirma em instantes.");
      if (rota.pago === "falhou") toast("Pagamento não concluído. Tente de novo nos Planos.");
      const u = await garantirSessao();
      if (u && u.papel === "DONO" && Store.temAuthReal() && !u.demo) {
        if (u.slug) Store.setLoja(u.slug);
        painelSecao = rota.pago === "ok" ? "planos" : painelSecao;
        await entrarApp({ modo: "dono", tab: "painel" });
        return;
      }
      mostrarLogin({ intent: "dono" });
      return;
    }

    // entry / gate — login automático se já entrou antes
    const u = await garantirSessao();
    if (u && Store.temAuthReal() && !u.demo) {
      if (u.papel === "DONO") {
        if (u.slug) Store.setLoja(u.slug);
        await entrarApp({ modo: "dono", tab: "painel" });
        return;
      }
      const slug = Store.getSlug();
      if (slug) {
        await entrarLoja(slug);
        return;
      }
    }
    mostrarEntry();
  }

  /** Token + usuário em cache; valida na API se necessário. */
  async function garantirSessao() {
    let u = usuarioLogado();
    if (Store.temAuthReal() && u) return u;
    if (Store.temAuthReal()) return Store.restaurarSessao();
    return null;
  }

  function usuarioLogado() {
    return Store.getUsuario();
  }

  function irParaLogin() {
    setHash("/login");
  }

  function aplicarPaywallDono(st) {
    let bar = $("#paywall-dono");
    if (!bar) {
      bar = document.createElement("div");
      bar.id = "paywall-dono";
      bar.className = "paywall-dono oculto";
      $("#tela-app")?.prepend(bar);
    }
    if (!st || !st.bloqueada) {
      bar.classList.add("oculto");
      bar.innerHTML = "";
      return;
    }
    bar.classList.remove("oculto");
    bar.innerHTML = `
      <strong>Trial encerrado</strong>
      <span>Assine um plano para liberar o painel, agenda e novos agendamentos.</span>
      <button type="button" id="btn-paywall-planos">Ver planos</button>`;
    $("#btn-paywall-planos", bar)?.addEventListener("click", () => {
      painelSecao = "planos";
      ativarTab("painel");
    });
  }

  function esconderPaywallDono() {
    const bar = $("#paywall-dono");
    if (bar) {
      bar.classList.add("oculto");
      bar.innerHTML = "";
    }
  }

  function atualizarHeaderUsuario() {
    const u = usuarioLogado();
    const el = $("#header-user");
    if (!el) return;
    if (!u) {
      el.textContent = rotaAtual.tipo === "loja" ? "Cliente · visitante" : "Visitante";
      return;
    }
    if (rotaAtual.tipo === "dono" || (u.papel === "DONO" && Store.isDono())) {
      el.textContent = `Dono · ${u.nome}`;
      return;
    }
    el.textContent = `Cliente · ${u.nome}`;
  }

  function atualizarNavDono() {
    const tab = $("#tab-painel");
    const btnOpcoes = $("#btn-painel-opcoes");
    const btnAssinatura = $("#btn-assinatura");
    const dono = Store.isDono();
    if (tab) tab.classList.toggle("oculto", !dono);
    if (btnOpcoes) btnOpcoes.classList.toggle("oculto", !dono);
    if (btnAssinatura) btnAssinatura.classList.toggle("oculto", !dono);
  }

  function alternarModoAuth(cadastro) {
    $("#bloco-login")?.classList.toggle("oculto", cadastro);
    $("#bloco-cadastro")?.classList.toggle("oculto", !cadastro);
    $("#auth-titulo").textContent = cadastro ? "Criar conta" : "Entrar";
  }

  /* ---------- tabs ---------- */

  function ativarTab(nome) {
    $$(".tab").forEach((t) => t.classList.toggle("ativa", t.dataset.tab === nome));
    $$(".aba-conteudo").forEach((a) =>
      a.classList.toggle("ativa", a.id === `aba-${nome}`)
    );
    const titulos = {
      agenda: "Agenda",
      perfil: "Perfil",
      opcoes: "Opções",
      painel: "Painel",
    };
    const h = $("#titulo-aba");
    if (h) h.textContent = titulos[nome] || "Agenda";
    const fab = $("#btn-agendar");
    if (fab) {
      const showFab = nome === "agenda" && rotaAtual.tipo === "loja";
      fab.classList.toggle("oculto", !showFab);
    }
    if (nome === "agenda") renderAgenda();
    if (nome === "perfil") renderPerfil();
    if (nome === "painel") renderPainel();
  }

  /* ---------- lembretes — cliente usa Google Agenda ---------- */

  function pedirPermissaoNotificacao() {
    /* desativado */
  }

  function limparLembretes() {
    lembreteTimers.forEach((id) => clearTimeout(id));
    lembreteTimers = [];
    lembreteIds = new Set();
  }

  function agendarLembretesBrowser() {
    /* desativado */
  }

  /* ---------- agenda ---------- */

  function renderAgenda() {
    const lista = $("#lista-agenda");
    const vazia = $("#agenda-vazia");
    const itens = Store.proximos(B().janelaAgendaDias);

    atualizarMarcasUI();

    if (!itens.length) {
      show(vazia);
      lista.innerHTML = "";
      return;
    }

    hide(vazia);
    lista.innerHTML = itens
      .map((ag) => {
        const serv = Store.getServico(ag.servicoId);
        const barb = Store.getBarbeiro(ag.barbeiroId) || {
          nome: ag.barbeiroNome,
          iniciais: ag.barbeiroIniciais,
          cor: ag.barbeiroCor,
        };
        const d = U().parseISODate(ag.data);
        const dia = U().diaSemanaCurto(d);
        const fim = ag.fim || U().fimAtendimento(ag.hora, serv?.duracaoMin || 30);
        const fechado = ag.status === "FINALIZADO" || ag.status === "NAO_COMPARECEU";
        return `
          <article class="card-agendamento" data-id="${ag.id}">
            <div class="card-agendamento-faixa"></div>
            <div class="card-agendamento-corpo">
              <div class="card-agendamento-topo">
                <span>${dia} · ${ag.hora} até ${fim}</span>
                <span>${U().formatarDataBR(ag.data)}</span>
              </div>
              <div class="card-agendamento-info">
                ${avatarHtml(barb)}
                <div>
                  <strong>${barb?.nome || ag.barbeiroNome || "Barbeiro"}</strong>
                  <p>${serv?.nome || ag.servicoNome || "Serviço"}</p>
                  <div class="tags-ag-inline">
                    ${fechado ? `<span class="badge-status ${ag.status.toLowerCase()}">${ag.status === "FINALIZADO" ? "Atendido" : "Não compareceu"}</span>` : ""}
                  </div>
                </div>
                ${fechado ? "" : `<button type="button" class="btn-menu-ag" data-id="${ag.id}" aria-label="Opções">⋮</button>`}
              </div>
              ${fechado ? "" : `<button type="button" class="btn-google-agenda-card" data-id="${ag.id}">Salvar no Google Agenda</button>`}
            </div>
          </article>`;
      })
      .join("");

    $$(".btn-menu-ag", lista).forEach((btn) => {
      btn.addEventListener("click", (e) => {
        e.stopPropagation();
        cancelarAgendamento(btn.dataset.id);
      });
    });
    $$(".btn-google-agenda-card", lista).forEach((btn) => {
      btn.addEventListener("click", (e) => {
        e.stopPropagation();
        const ag = itens.find((a) => String(a.id) === String(btn.dataset.id));
        if (ag) abrirGoogleAgenda(ag);
      });
    });
  }

  async function cancelarAgendamento(id) {
    const ag = Store.get().agendamentos.find((a) => String(a.id) === String(id));
    if (!ag) return;
    const ok = confirm(
      `Cancelar agendamento de ${U().formatarDataBR(ag.data)} às ${ag.hora}?\n\n` +
        B().politicaCancelamento.texto
    );
    if (!ok) return;
    try {
      await Store.cancelarAgendamento(id);
      toast("Agendamento cancelado");
      renderAgenda();
    } catch (e) {
      toast(e.message || "Erro ao cancelar");
    }
  }

  /* ---------- perfil ---------- */

  function renderPerfil() {
    const u = usuarioLogado();
    if (!u) return;
    $("#perfil-nome").textContent = u.nome;
    $("#perfil-email").textContent = u.email || "—";
    const total = Store.get().agendamentos.filter((a) => a.status !== "CANCELADO").length;
    $("#perfil-total").textContent = String(total);
    const ini = u.nome
      .split(" ")
      .filter(Boolean)
      .slice(0, 2)
      .map((p) => p[0])
      .join("")
      .toUpperCase();
    const el = $("#perfil-iniciais");
    if (el) el.textContent = ini || "CL";
    const badge = $("#perfil-papel");
    if (badge) {
      badge.textContent = u.papel === "DONO" ? "Dono" : u.demo ? "Demo (local)" : "Cliente";
      badge.classList.toggle("dono", u.papel === "DONO");
    }
  }

  /* ---------- wizard ---------- */

  function abrirWizard() {
    if (!Store.getSlug()) {
      toast("Abra uma loja pelo link para agendar");
      return;
    }
    resetBooking();
    show($("#wizard"));
    irPasso("escolha");
  }

  function fecharWizard() {
    hide($("#wizard"));
    resetBooking();
  }

  function voltarPasso() {
    const { path } = booking;
    const ativo = $(".passo.ativo")?.dataset.passo;
    if (ativo === "escolha") fecharWizard();
    else if (ativo === "servicos") irPasso(path === "profissional" ? "barbeiro" : "escolha");
    else if (ativo === "barbeiro") irPasso("escolha");
    else if (ativo === "profissional") irPasso("servicos");
    else if (ativo === "confirmar") irPasso("profissional");
    else if (ativo === "sucesso") fecharWizard();
    else fecharWizard();
  }

  function irPasso(nome) {
    $$(".passo").forEach((p) => p.classList.toggle("ativo", p.dataset.passo === nome));
    salvarDraft();
    atualizarMarcasUI();

    if (nome === "escolha") renderEscolha();
    if (nome === "servicos") renderServicos();
    if (nome === "barbeiro") renderBarbeiroPick();
    if (nome === "profissional") renderProfissional();
    if (nome === "confirmar") renderConfirmar();
    if (nome === "sucesso") renderSucesso();

    const voltar = $("#btn-wizard-voltar");
    const proximo = $("#btn-wizard-proximo");
    const agendar = $("#btn-wizard-agendar");
    hide(voltar);
    hide(proximo);
    hide(agendar);

    if (nome === "escolha") show(voltar);
    else if (nome === "servicos") {
      show(voltar);
      show(proximo);
      proximo.textContent = "Próximo";
    } else if (nome === "barbeiro") {
      show(voltar);
      show(proximo);
      proximo.textContent = "Próximo";
    } else if (nome === "profissional") {
      show(voltar);
      show(proximo);
      proximo.textContent = "Próximo";
    } else if (nome === "confirmar") {
      show(voltar);
      show(agendar);
    } else if (nome === "sucesso") {
      hide($("#wizard-footer"));
    }

    if (nome !== "sucesso") show($("#wizard-footer"));
  }

  function renderEscolha() {
    /* estático no HTML */
  }

  function renderServicos() {
    const busca = ($("#busca-servico")?.value || "").toLowerCase().trim();
    const lista = $("#lista-servicos");
    const filtrados = Store.getServicos().filter(
      (s) => !busca || s.nome.toLowerCase().includes(busca)
    );

    if (Store.catalogoOffline()) {
      $("#aviso-offline")?.classList.remove("oculto");
    } else {
      $("#aviso-offline")?.classList.add("oculto");
    }

    lista.innerHTML = filtrados.length
      ? filtrados
          .map((s) => {
            const on = Number(booking.servicoId) === Number(s.id);
            return `
              <button type="button" class="linha-servico ${on ? "selecionado" : ""}" data-id="${s.id}">
                <span class="linha-servico-nome">${s.nome}</span>
                <span class="linha-servico-meta">${s.duracaoMin} min</span>
                <span class="linha-servico-preco">${U().dinheiro(s.preco)}</span>
                <span class="linha-servico-acao" aria-hidden="true">${on ? "✓" : "+"}</span>
              </button>`;
          })
          .join("")
      : `<p class="lista-vazia">Nenhum serviço disponível</p>`;

    $$(".linha-servico", lista).forEach((btn) => {
      btn.addEventListener("click", () => {
        booking.servicoId = Number(btn.dataset.id);
        booking.hora = null;
        salvarDraft();
        renderServicos();
        atualizarBtnProximo();
      });
    });

    const chip = $("#chip-barbeiro-wizard");
    if (chip) {
      if (booking.path === "profissional" && booking.barbeiroId) {
        const b = Store.getBarbeiro(booking.barbeiroId);
        chip.classList.remove("oculto");
        $("#chip-barbeiro-nome").textContent = b?.nome || "—";
        const av = $("#chip-barbeiro-iniciais");
        if (av) {
          if (b?.fotoData) {
            av.classList.add("avatar-foto");
            av.innerHTML = `<img src="${b.fotoData}" alt="" />`;
          } else {
            av.classList.remove("avatar-foto");
            av.textContent = b?.iniciais || "?";
          }
          av.style.background = b?.cor || "#333";
        }
      } else {
        chip.classList.add("oculto");
      }
    }

    atualizarBtnProximo();
  }

  function renderBarbeiroPick() {
    const lista = $("#lista-barbeiro-pick");
    const barbeiros = Store.getBarbeiros();
    lista.innerHTML = barbeiros
      .map((b) => {
        const on = Number(booking.barbeiroId) === Number(b.id);
        return `
          <button type="button" class="card-pick-barbeiro ${on ? "selecionado" : ""}" data-id="${b.id}">
            ${avatarHtml(b)}
            <strong>${b.nome}</strong>
            ${on ? '<span class="pick-check">✓</span>' : ""}
          </button>`;
      })
      .join("");

    $$(".card-pick-barbeiro", lista).forEach((btn) => {
      btn.addEventListener("click", () => {
        booking.barbeiroId = Number(btn.dataset.id);
        booking.hora = null;
        booking.data = null;
        booking.semPreferencia = false;
        salvarDraft();
        renderBarbeiroPick();
        atualizarBtnProximo();
      });
    });
    atualizarBtnProximo();
  }

  function renderCalendario() {
    const { mes, ano } = U().mesAno(calCursor);
    $("#cal-mes").textContent = mes;
    $("#cal-ano").textContent = String(ano);

    const ref = new Date(dataSelecionada);
    if (
      ref.getMonth() !== calCursor.getMonth() ||
      ref.getFullYear() !== calCursor.getFullYear()
    ) {
      ref.setFullYear(calCursor.getFullYear(), calCursor.getMonth(), 1);
    }

    const domingo = new Date(ref);
    domingo.setDate(ref.getDate() - ref.getDay());

    const dias = $("#cal-dias");
    const hoje = new Date();
    hoje.setHours(0, 0, 0, 0);

    dias.innerHTML = "";
    for (let i = 0; i < 7; i++) {
      const d = new Date(domingo);
      d.setDate(domingo.getDate() + i);
      const iso = U().toISODate(d);
      const selecionado = U().isMesmoDia(d, dataSelecionada);
      const ehHoje = U().isMesmoDia(d, hoje);
      const passado = d < hoje;
      const funciona = B().agenda.diasFuncionamento.includes(d.getDay());

      const btn = document.createElement("button");
      btn.type = "button";
      btn.className =
        "cal-dia" +
        (selecionado ? " selecionado" : "") +
        (ehHoje ? " hoje" : "") +
        (passado || !funciona ? " disabled" : "");
      btn.disabled = passado || !funciona;
      btn.innerHTML = `<span>${U().diaSemanaLetra(d)}</span><strong>${d.getDate()}</strong>`;
      btn.addEventListener("click", async () => {
        dataSelecionada = d;
        booking.data = iso;
        booking.hora = null;
        salvarDraft();
        renderCalendario();
        await renderSlots();
        atualizarBtnProximo();
      });
      dias.appendChild(btn);
    }

    if (!booking.data) {
      booking.data = U().toISODate(dataSelecionada);
    }
  }

  async function renderProfissional() {
    const serv = Store.getServico(booking.servicoId);
    const barb = booking.barbeiroId ? Store.getBarbeiro(booking.barbeiroId) : null;

    $("#chip-servico-nome").textContent = serv?.nome || "—";
    $("#chip-servico-meta").textContent = serv
      ? `${serv.duracaoMin} min · ${U().dinheiro(serv.preco)}`
      : "";

    const chipBarb = $("#chip-barbeiro-prof");
    if (booking.path === "profissional" && barb) {
      chipBarb?.classList.remove("oculto");
      $("#chip-barbeiro-prof-nome").textContent = barb.nome;
    } else {
      chipBarb?.classList.add("oculto");
    }

    renderCalendario();
    await renderSlots();
    atualizarBtnProximo();
  }

  function bindSemPref() {
    $("#btn-sem-pref")?.addEventListener("click", async () => {
      booking.semPreferencia = !booking.semPreferencia;
      booking.barbeiroId = null;
      booking.hora = null;
      salvarDraft();
      await renderSlots();
      atualizarBtnProximo();
    });
  }

  async function renderSlots() {
    const box = $("#lista-barbeiros");
    const dataIso = booking.data || U().toISODate(dataSelecionada);

    box.innerHTML = `<p class="carregando-slots">Carregando horários…</p>`;

    let html = "";
    if (booking.path === "servico") {
      html += `
        <button type="button" class="btn-sem-pref ${booking.semPreferencia ? "ativo" : ""}" id="btn-sem-pref">
          ${booking.semPreferencia ? "✓ SEM PREFERÊNCIA" : "SEM PREFERÊNCIA"}
        </button>`;
    }

    if (booking.path === "servico" && booking.semPreferencia) {
      const horas = new Set();
      for (const b of Store.getBarbeiros()) {
        try {
          (await Store.fetchSlots(b.id, dataIso, booking.servicoId)).forEach((h) =>
            horas.add(h)
          );
        } catch {
          /* ignora */
        }
      }
      const ordenadas = [...horas].sort();
      const slotsHtml = ordenadas.length
        ? ordenadas
            .map((h) => {
              const on = booking.hora === h;
              return `<button type="button" class="slot ${on ? "selecionado" : ""}" data-hora="${h}">${h}</button>`;
            })
            .join("")
        : `<p class="sem-slot">Sem horários neste dia</p>`;

      html += `
        <div class="card-barbeiro">
          <div class="card-barbeiro-topo">
            <div class="avatar" style="background:#555">★</div>
            <strong>Qualquer profissional</strong>
          </div>
          <div class="grade-slots">${slotsHtml}</div>
        </div>`;

      box.innerHTML = html;
      bindSemPref();

      $$(".slot", box).forEach((btn) => {
        btn.addEventListener("click", () => {
          booking.hora = btn.dataset.hora;
          booking.barbeiroId = null;
          booking.semPreferencia = true;
          salvarDraft();
          $$(".slot", box).forEach((s) =>
            s.classList.toggle("selecionado", s.dataset.hora === booking.hora)
          );
          atualizarBtnProximo();
        });
      });
      return;
    }

    const barbeiros =
      booking.path === "profissional" && booking.barbeiroId
        ? [Store.getBarbeiro(booking.barbeiroId)].filter(Boolean)
        : Store.getBarbeiros();

    for (const b of barbeiros) {
      let slots = [];
      try {
        slots = await Store.fetchSlots(b.id, dataIso, booking.servicoId);
      } catch {
        slots = [];
      }

      const slotsHtml = slots.length
        ? slots
            .map((h) => {
              const on =
                Number(booking.barbeiroId) === Number(b.id) && booking.hora === h;
              return `<button type="button" class="slot ${on ? "selecionado" : ""}" data-barbeiro="${b.id}" data-hora="${h}">${h}</button>`;
            })
            .join("")
        : `<p class="sem-slot">Sem horários neste dia</p>`;

      html += `
        <div class="card-barbeiro">
          <div class="card-barbeiro-topo">
            ${avatarHtml(b)}
            <strong>${b.nome}</strong>
          </div>
          <div class="grade-slots">${slotsHtml}</div>
        </div>`;
    }

    box.innerHTML = html;
    bindSemPref();

    $$(".slot", box).forEach((btn) => {
      btn.addEventListener("click", () => {
        booking.barbeiroId = Number(btn.dataset.barbeiro);
        booking.hora = btn.dataset.hora;
        booking.semPreferencia = false;
        salvarDraft();
        $$(".slot", box).forEach((s) =>
          s.classList.toggle(
            "selecionado",
            Number(s.dataset.barbeiro) === booking.barbeiroId &&
              s.dataset.hora === booking.hora
          )
        );
        atualizarBtnProximo();
      });
    });
  }

  function horarioDefinido() {
    return !!(
      booking.data &&
      booking.hora &&
      (booking.barbeiroId || booking.semPreferencia)
    );
  }

  function atualizarBtnProximo() {
    const prox = $("#btn-wizard-proximo");
    const agendar = $("#btn-wizard-agendar");
    const ativo = $(".passo.ativo")?.dataset.passo;

    if (prox) {
      if (ativo === "servicos") {
        prox.disabled = !booking.servicoId;
      } else if (ativo === "barbeiro") {
        prox.disabled = !booking.barbeiroId;
      } else if (ativo === "profissional") {
        prox.disabled = !horarioDefinido();
      }
    }

    if (agendar) {
      agendar.disabled = !(booking.servicoId && horarioDefinido());
    }
  }

  function renderConfirmar() {
    const serv = Store.getServico(booking.servicoId);
    const semPref = booking.semPreferencia && !booking.barbeiroId;
    const barb = semPref ? null : Store.getBarbeiro(booking.barbeiroId);
    const fim = U().fimAtendimento(booking.hora, serv?.duracaoMin || 30);

    $("#conf-data").textContent = U().formatarDataBR(booking.data);
    $("#conf-estab").textContent = B().estabelecimento;
    $("#conf-barbeiro").textContent = semPref
      ? "Qualquer profissional"
      : barb?.nome || "—";
    $("#conf-servico").textContent = serv?.nome || "—";
    $("#conf-horario").textContent = `${booking.hora} até ${fim}`;
    const av = $("#conf-avatar");
    if (av) {
      if (!semPref && barb?.fotoData) {
        av.classList.add("avatar-foto");
        av.innerHTML = `<img src="${barb.fotoData}" alt="" />`;
      } else {
        av.classList.remove("avatar-foto");
        av.textContent = semPref ? "★" : barb?.iniciais || "?";
      }
      av.style.background = semPref ? "#555" : barb?.cor || "#333";
    }
    $("#conf-politica").textContent = B().politicaCancelamento.texto;
    $("#conf-obs").value = booking.observacao || "";

    const aviso = $("#conf-aviso-auth");
    if (aviso) {
      aviso.classList.toggle("oculto", Store.temAuthReal());
    }
  }

  async function confirmarAgendamento() {
    if (!Store.temAuthReal()) {
      toast("Faça login (e-mail ou Google) para confirmar o agendamento");
      irParaLogin();
      return;
    }

    const slug = Store.getSlug();
    if (!slug) {
      toast("Loja não definida");
      return;
    }

    const serv = Store.getServico(booking.servicoId);
    if (!serv || !booking.hora || !booking.data || !(booking.barbeiroId || booking.semPreferencia)) {
      toast("Selecione um horário para agendar");
      return;
    }

    booking.observacao = $("#conf-obs")?.value?.trim() || "";
    const btn = $("#btn-wizard-agendar");
    if (btn) {
      btn.disabled = true;
      btn.textContent = "Agendando…";
    }

    try {
      const res = await API.post("/api/agendamentos", {
        slug,
        barbeiroId: booking.semPreferencia ? null : booking.barbeiroId,
        servicoId: booking.servicoId,
        data: booking.data,
        horaInicio: horaApi(booking.hora),
        observacao: booking.observacao,
        semPreferencia: !!booking.semPreferencia,
      });

      ultimoAgendamento = Store.normAgendamento(res);
      Store.addAgendamento(ultimoAgendamento);
      Store.clearDraft();
      irPasso("sucesso");
      abrirGoogleAgenda(ultimoAgendamento);
      renderAgenda();
    } catch (e) {
      if (e.offline) {
        toast("Servidor offline. Conecte o backend para confirmar.");
      } else if (e.status === 503 || e.status === 402) {
        toast(
          e.message ||
            "Esta barbearia não está aceitando agendamentos no momento."
        );
      } else {
        toast(e.message || "Erro ao agendar");
      }
    } finally {
      if (btn) {
        btn.disabled = false;
        btn.textContent = "Agendar";
      }
    }
  }

  function renderSucesso() {
    const ag = ultimoAgendamento;
    if (!ag) return;
    const serv = Store.getServico(ag.servicoId);
    const barb = Store.getBarbeiro(ag.barbeiroId);
    const fim = ag.fim || U().fimAtendimento(ag.hora, serv?.duracaoMin || 30);

    $("#succ-titulo").textContent = "Salve no Google Agenda";
    $("#succ-detalhe").textContent =
      `${serv?.nome || ag.servicoNome} com ${barb?.nome || ag.barbeiroNome}\n` +
      `${U().formatarDataBR(ag.data)} · ${ag.hora} até ${fim}`;

    const linkCal = $("#btn-google-cal");
    if (linkCal) {
      linkCal.href = googleCalendarUrl(ag);
      linkCal.onclick = (ev) => {
        ev.preventDefault();
        abrirGoogleAgenda(ag);
      };
    }
  }

  function avancarPasso() {
    const ativo = $(".passo.ativo")?.dataset.passo;
    if (ativo === "servicos") {
      irPasso("profissional");
    } else if (ativo === "barbeiro") {
      irPasso("servicos");
    } else if (ativo === "profissional") {
      irPasso("confirmar");
    }
  }

  /* ---------- painel do dono ---------- */

  function renderPainel() {
    renderPainelNav();
    if (painelSecao === "resumo") renderPainelResumo();
    if (painelSecao === "agendamentos") renderPainelAgendamentos();
    if (painelSecao === "barbeiros") renderPainelBarbeiros();
    if (painelSecao === "servicos") renderPainelServicos();
    if (painelSecao === "bloqueios") renderPainelBloqueios();
    if (painelSecao === "loja") renderPainelLoja();
    if (painelSecao === "planos") renderPainelPlanos();
  }

  function renderPainelNav() {
    $$(".painel-nav-btn").forEach((b) =>
      b.classList.toggle("ativo", b.dataset.secao === painelSecao)
    );
  }

  const brl = (v) =>
    Number(v || 0).toLocaleString("pt-BR", {
      style: "currency",
      currency: "BRL",
    });

  function intervaloDoPeriodo() {
    const hoje = new Date();
    hoje.setHours(0, 0, 0, 0);
    const iso = (d) => U().toISODate(d);

    if (resumoPeriodo === "semana") {
      const ini = new Date(hoje);
      const diaSemana = (ini.getDay() + 6) % 7;
      ini.setDate(ini.getDate() - diaSemana);
      const fim = new Date(ini);
      fim.setDate(fim.getDate() + 6);
      return { inicio: iso(ini), fim: iso(fim), rotulo: "Esta semana" };
    }
    if (resumoPeriodo === "mes") {
      const ini = new Date(hoje.getFullYear(), hoje.getMonth(), 1);
      const fim = new Date(hoje.getFullYear(), hoje.getMonth() + 1, 0);
      return { inicio: iso(ini), fim: iso(fim), rotulo: "Este mês" };
    }
    if (resumoPeriodo === "custom") {
      const ini = resumoInicio || iso(hoje);
      const fim = resumoFim || ini;
      return { inicio: ini, fim, rotulo: "Período escolhido" };
    }
    return { inicio: iso(hoje), fim: iso(hoje), rotulo: "Hoje" };
  }

  function renderPainelResumo() {
    const box = $("#painel-conteudo");
    const { inicio, fim } = intervaloDoPeriodo();
    const btn = (id, txt) =>
      `<button type="button" class="chip-periodo ${resumoPeriodo === id ? "ativo" : ""}" data-periodo="${id}">${txt}</button>`;

    box.innerHTML = `
      <div class="resumo-filtros">
        ${btn("hoje", "Hoje")}
        ${btn("semana", "Semana")}
        ${btn("mes", "Mês")}
        ${btn("custom", "Personalizado")}
      </div>
      <div class="resumo-datas ${resumoPeriodo === "custom" ? "" : "oculto"}">
        <label>De<input type="date" id="resumo-de" value="${inicio}" /></label>
        <label>Até<input type="date" id="resumo-ate" value="${fim}" /></label>
        <button type="button" id="btn-resumo-aplicar">Aplicar</button>
      </div>
      <button type="button" class="btn-assinatura-painel" id="btn-assinatura-resumo">✦ Ver planos Lâmina Pro</button>
      <div id="resumo-dados"><p class="carregando-slots">Carregando…</p></div>`;

    $$(".chip-periodo", box).forEach((b) => {
      b.addEventListener("click", () => {
        resumoPeriodo = b.dataset.periodo;
        renderPainelResumo();
      });
    });

    $("#btn-resumo-aplicar", box)?.addEventListener("click", () => {
      const de = $("#resumo-de", box)?.value;
      const ate = $("#resumo-ate", box)?.value;
      if (!de || !ate) return toast("Escolha as duas datas");
      if (ate < de) return toast("A data final não pode ser antes da inicial");
      resumoInicio = de;
      resumoFim = ate;
      carregarResumo();
    });

    $("#btn-assinatura-resumo", box)?.addEventListener("click", () => {
      painelSecao = "planos";
      renderPainel();
    });

    carregarResumo();
  }

  async function carregarResumo() {
    const alvo = $("#resumo-dados");
    if (!alvo) return;
    const { inicio, fim, rotulo } = intervaloDoPeriodo();
    try {
      const r = await Store.donoResumo(inicio, fim);
      alvo.innerHTML = htmlResumo(r, rotulo);
    } catch (e) {
      alvo.innerHTML = `<p class="lista-vazia erro">${e.message || "Erro ao carregar resumo"}</p>`;
    }
  }

  function htmlResumo(r, rotulo) {
    const periodoTxt =
      r.inicio === r.fim
        ? U().formatarDataBR(r.inicio)
        : `${U().formatarDataBR(r.inicio)} — ${U().formatarDataBR(r.fim)}`;

    return `
      <p class="resumo-periodo">${rotulo} · ${periodoTxt}</p>

      <div class="kpi-grid">
        <article class="kpi kpi-destaque">
          <span class="kpi-rot">Faturamento realizado</span>
          <strong class="kpi-val">${brl(r.realizado.faturamento)}</strong>
          <small>${r.realizado.atendimentos} atendimento(s)</small>
        </article>
        <article class="kpi">
          <span class="kpi-rot">Previsto no período</span>
          <strong class="kpi-val">${brl(r.previsto.faturamento)}</strong>
          <small>${r.previsto.atendimentos} agendado(s)</small>
        </article>
        <article class="kpi">
          <span class="kpi-rot">Ticket médio</span>
          <strong class="kpi-val">${brl(r.realizado.ticketMedio)}</strong>
          <small>por atendimento</small>
        </article>
        <article class="kpi">
          <span class="kpi-rot">Ocupação da agenda</span>
          <strong class="kpi-val">${r.ocupacao}%</strong>
          <small>${r.slotsOcupados} de ${r.slotsTotais} horários</small>
        </article>
        <article class="kpi kpi-alerta">
          <span class="kpi-rot">Não compareceu</span>
          <strong class="kpi-val">${r.naoCompareceu.atendimentos}</strong>
          <small>${brl(r.naoCompareceu.valorPerdido)} perdidos · ${r.taxaNoShow}%</small>
        </article>
        <article class="kpi">
          <span class="kpi-rot">Clientes atendidos</span>
          <strong class="kpi-val">${r.clientes.atendidos}</strong>
          <small>${r.clientes.novos} novo(s) · ${r.clientes.recorrentes} recorrente(s)</small>
        </article>
      </div>

      ${blocoBarbeiros(r.porBarbeiro)}
      ${blocoServicos(r.porServico)}
      ${blocoSerie(r.serie)}
      ${r.cancelados ? `<p class="resumo-nota">${r.cancelados} agendamento(s) cancelado(s) no período.</p>` : ""}
      <p class="resumo-nota">Atendimentos que já passaram e não foram fechados contam como finalizados.</p>`;
  }

  function blocoBarbeiros(lista) {
    if (!lista || !lista.length) {
      return `<h3 class="resumo-titulo">Por profissional</h3><p class="lista-vazia">Nenhum profissional ativo</p>`;
    }
    const teto = Math.max(...lista.map((b) => Number(b.faturamento) || 0), 1);
    const linhas = lista
      .map((b) => {
        const pct = Math.round(((Number(b.faturamento) || 0) / teto) * 100);
        return `
        <article class="card-barbeiro-perf">
          ${avatarHtml(b)}
          <div class="perf-info">
            <div class="perf-topo">
              <strong>${b.nome}</strong>
              <span class="perf-valor">${brl(b.faturamento)}</span>
            </div>
            <div class="perf-barra"><i style="width:${pct}%;background:${b.cor}"></i></div>
            <small>${b.atendimentos} atend. · ticket ${brl(b.ticketMedio)} · ocupação ${b.ocupacao}%${
              b.naoCompareceu ? ` · ${b.naoCompareceu} falta(s)` : ""
            }</small>
          </div>
        </article>`;
      })
      .join("");
    return `<h3 class="resumo-titulo">Por profissional</h3>${linhas}`;
  }

  function blocoServicos(lista) {
    if (!lista || !lista.length) return "";
    const linhas = lista
      .slice(0, 6)
      .map(
        (s) => `
        <li class="linha-servico">
          <span>${s.nome}</span>
          <span class="qtd">${s.quantidade}x</span>
          <strong>${brl(s.faturamento)}</strong>
        </li>`
      )
      .join("");
    return `<h3 class="resumo-titulo">Serviços mais vendidos</h3><ul class="lista-servicos-resumo">${linhas}</ul>`;
  }

  function blocoSerie(serie) {
    if (!serie || serie.length < 2) return "";
    const teto = Math.max(...serie.map((p) => Number(p.faturamento) || 0), 1);
    const barras = serie
      .map((p) => {
        const v = Number(p.faturamento) || 0;
        const alt = Math.max(2, Math.round((v / teto) * 100));
        const rot = p.chave.length === 7 ? p.chave.substring(5) : p.chave.substring(8);
        return `<div class="serie-col" title="${U().formatarDataBR(p.chave.length === 7 ? p.chave + "-01" : p.chave)}: ${brl(v)}">
          <i style="height:${alt}%"></i><span>${rot}</span>
        </div>`;
      })
      .join("");
    return `<h3 class="resumo-titulo">Faturamento no período</h3><div class="serie-grafico">${barras}</div>`;
  }

  const STATUS_ROTULO = {
    CONFIRMADO: "Confirmado",
    FINALIZADO: "Finalizado",
    NAO_COMPARECEU: "Não compareceu",
  };

  function jaPassou(ag) {
    const fim = ag.fim || ag.hora;
    return new Date(`${ag.data}T${fim}:00`) < new Date();
  }

  function filtrarAgenda(lista) {
    const hoje = U().toISODate(new Date());
    if (agendaFiltro === "hoje") return lista.filter((a) => a.data === hoje);
    if (agendaFiltro === "pendentes") {
      return lista.filter((a) => a.status === "CONFIRMADO" && jaPassou(a));
    }
    if (agendaFiltro === "proximos") {
      return lista.filter((a) => a.data > hoje || (a.data === hoje && !jaPassou(a)));
    }
    return lista;
  }

  async function renderPainelAgendamentos() {
    const box = $("#painel-conteudo");
    box.innerHTML = `<p class="carregando-slots">Carregando…</p>`;
    try {
      const todos = await Store.donoAgendamentos(30, 7);
      const pendentes = todos.filter((a) => a.status === "CONFIRMADO" && jaPassou(a)).length;
      const lista = filtrarAgenda(todos);
      const barbeiros = Store.getBarbeiros();

      const chip = (id, txt, extra = "") =>
        `<button type="button" class="chip-periodo ${agendaFiltro === id ? "ativo" : ""}" data-filtro="${id}">${txt}${extra}</button>`;

      const cards = lista.length
        ? lista.map((ag) => cardAgendamentoDono(ag, barbeiros)).join("")
        : `<p class="lista-vazia">Nenhum agendamento neste filtro</p>`;

      box.innerHTML = `
        <div class="resumo-filtros">
          ${chip("hoje", "Hoje")}
          ${chip("pendentes", "A fechar", pendentes ? ` <b>${pendentes}</b>` : "")}
          ${chip("proximos", "Próximos")}
          ${chip("todos", "Todos")}
        </div>
        ${cards}`;

      $$(".chip-periodo", box).forEach((b) => {
        b.addEventListener("click", () => {
          agendaFiltro = b.dataset.filtro;
          renderPainelAgendamentos();
        });
      });

      $$(".btn-mover", box).forEach((btn) => {
        btn.addEventListener("click", async () => {
          const id = btn.dataset.id;
          const sel = box.querySelector(`.sel-barbeiro[data-id="${id}"]`);
          if (!sel) return;
          await acaoCard(btn, "Movendo…", () =>
            Store.donoReatribuirBarbeiro(id, sel.value)
          );
        });
      });

      $$(".btn-status", box).forEach((btn) => {
        btn.addEventListener("click", async () => {
          const { id, status } = btn.dataset;
          const campo = box.querySelector(`.valor-cobrado[data-id="${id}"]`);
          const forma = box.querySelector(`.forma-pag[data-id="${id}"]`)?.value;
          const valor = status === "FINALIZADO" ? campo?.value : null;
          await acaoCard(btn, "Salvando…", () =>
            Store.donoAtualizarStatus(id, status, valor, status === "FINALIZADO" ? forma : null)
          );
          if (status === "FINALIZADO") {
            toast("Atendimento finalizado");
            painelSecao = "resumo";
            renderPainel();
          }
        });
      });
    } catch (e) {
      box.innerHTML = `<p class="lista-vazia erro">${e.message || "Erro ao carregar"}</p>`;
    }
  }

  function formaRotulo(f) {
    return (
      {
        PIX: "Pix",
        DINHEIRO: "Dinheiro",
        CARTAO: "Cartão",
        OUTRO: "Outro",
      }[f] || f
    );
  }

  async function acaoCard(btn, textoCarregando, fn) {
    const original = btn.textContent;
    btn.disabled = true;
    btn.textContent = textoCarregando;
    try {
      await fn();
      renderPainelAgendamentos();
    } catch (e) {
      toast(e.message || "Não foi possível salvar");
      btn.disabled = false;
      btn.textContent = original;
    }
  }

  function cardAgendamentoDono(ag, barbeiros) {
    const opcoes = barbeiros
      .map(
        (b) =>
          `<option value="${b.id}" ${Number(b.id) === Number(ag.barbeiroId) ? "selected" : ""}>${b.nome}</option>`
      )
      .join("");
    const valor = ag.valorCobrado != null ? ag.valorCobrado : ag.servicoPreco;
    const aberto = ag.status === "CONFIRMADO";
    const emAberto = aberto && jaPassou(ag);
    const formaAtual = ag.formaPagamento || "PIX";

    const fechamento = aberto
      ? `<div class="fechamento ${emAberto ? "urgente" : ""}">
          <label>Valor pago
            <input type="number" class="valor-cobrado" data-id="${ag.id}"
              min="0" step="0.01" value="${valor ?? ""}" />
          </label>
          <label>Forma
            <select class="forma-pag" data-id="${ag.id}">
              <option value="PIX" ${formaAtual === "PIX" ? "selected" : ""}>Pix</option>
              <option value="DINHEIRO" ${formaAtual === "DINHEIRO" ? "selected" : ""}>Dinheiro</option>
              <option value="CARTAO" ${formaAtual === "CARTAO" ? "selected" : ""}>Cartão</option>
              <option value="OUTRO" ${formaAtual === "OUTRO" ? "selected" : ""}>Outro</option>
            </select>
          </label>
          <button type="button" class="btn-status ok" data-id="${ag.id}" data-status="FINALIZADO">✓ Finalizar</button>
          <button type="button" class="btn-status falta" data-id="${ag.id}" data-status="NAO_COMPARECEU">✗ Não veio</button>
        </div>`
      : `<div class="fechamento">
          <span class="valor-final">${brl(valor)}${ag.formaPagamento ? " · " + formaRotulo(ag.formaPagamento) : ""}</span>
          <button type="button" class="btn-status reabrir" data-id="${ag.id}" data-status="CONFIRMADO">Reabrir</button>
        </div>`;

    return `
      <article class="card-painel-ag ${ag.semPreferencia ? "sem-pref" : ""} st-${ag.status.toLowerCase()}">
        <div class="card-painel-ag-topo">
          <strong>${U().formatarDataBR(ag.data)} · ${ag.hora}</strong>
          <span>${ag.clienteNome || "Cliente"}</span>
        </div>
        <p>${ag.servicoNome} — ${ag.barbeiroNome}</p>
        <div class="tags-ag">
          <span class="badge-status ${ag.status.toLowerCase()}">${STATUS_ROTULO[ag.status] || ag.status}</span>
          ${ag.semPreferencia ? `<span class="tag-sem-pref">Sem preferência</span>` : ""}
          ${emAberto ? `<span class="tag-pendente">Aguardando fechamento</span>` : ""}
        </div>
        ${ag.observacao ? `<small>Obs: ${ag.observacao}</small>` : ""}
        ${fechamento}
        <div class="reatribuir">
          <label>Profissional</label>
          <select class="sel-barbeiro" data-id="${ag.id}">${opcoes}</select>
          <button type="button" class="btn-mover" data-id="${ag.id}">Mover</button>
        </div>
      </article>`;
  }

  async function renderPainelBarbeiros() {
    const box = $("#painel-conteudo");
    box.innerHTML = `<p class="carregando-slots">Carregando…</p>`;
    try {
      const lista = await Store.donoBarbeiros();
      box.innerHTML = `
        <button type="button" class="btn-painel-add" id="btn-novo-barbeiro">+ Novo barbeiro</button>
        <div id="form-barbeiro" class="form-painel oculto"></div>
        ${lista
          .map(
            (b) => `
          <article class="card-painel-item" data-id="${b.id}">
            ${avatarHtml(b)}
            <div class="card-painel-item-info">
              <strong>${b.nome}</strong>
              <span class="${b.ativo ? "tag-ativo" : "tag-inativo"}">${b.ativo ? "Ativo" : "Inativo"}</span>
            </div>
            <button type="button" class="btn-editar" data-id="${b.id}">Editar</button>
          </article>`
          )
          .join("")}`;
      $("#btn-novo-barbeiro")?.addEventListener("click", () => mostrarFormBarbeiro());
      $$(".btn-editar", box).forEach((btn) => {
        btn.addEventListener("click", () => {
          const b = lista.find((x) => Number(x.id) === Number(btn.dataset.id));
          mostrarFormBarbeiro(b);
        });
      });
    } catch (e) {
      box.innerHTML = `<p class="lista-vazia erro">${e.message || "Erro"}</p>`;
    }
  }

  function mostrarFormBarbeiro(b) {
    const form = $("#form-barbeiro");
    form.classList.remove("oculto");
    let fotoData = b?.fotoData || null;
    form.innerHTML = `
      <h4>${b ? "Editar barbeiro" : "Novo barbeiro"}</h4>
      <label>Nome<input type="text" id="fb-nome" value="${b?.nome || ""}" /></label>
      <label>Iniciais<input type="text" id="fb-iniciais" maxlength="4" value="${b?.iniciais || ""}" /></label>
      <label>Cor<input type="color" id="fb-cor" value="${b?.cor || "#3d3d3d"}" /></label>
      <label>Foto
        <input type="file" id="fb-foto" accept="image/*" />
        <div id="fb-foto-preview" class="foto-preview ${fotoData ? "" : "oculto"}">
          ${fotoData ? `<img src="${fotoData}" alt="" />` : ""}
        </div>
      </label>
      <label class="check-label"><input type="checkbox" id="fb-ativo" ${b?.ativo !== false ? "checked" : ""} /> Ativo</label>
      <div class="form-painel-acoes">
        <button type="button" class="btn btn-outline" id="fb-cancelar">Cancelar</button>
        <button type="button" class="btn btn-solid" id="fb-salvar">Salvar</button>
      </div>`;

    $("#fb-foto")?.addEventListener("change", async (e) => {
      const file = e.target.files?.[0];
      if (!file) return;
      try {
        fotoData = await U().comprimirImagem(file, 400, 0.7);
        const prev = $("#fb-foto-preview");
        if (prev) {
          prev.classList.remove("oculto");
          prev.innerHTML = `<img src="${fotoData}" alt="" />`;
        }
      } catch {
        toast("Não foi possível processar a foto");
      }
    });

    $("#fb-cancelar")?.addEventListener("click", () => form.classList.add("oculto"));
    $("#fb-salvar")?.addEventListener("click", async () => {
      const body = {
        nome: $("#fb-nome").value.trim(),
        iniciais: $("#fb-iniciais").value.trim(),
        cor: $("#fb-cor").value,
        ativo: $("#fb-ativo").checked,
        fotoData: fotoData,
      };
      if (!body.nome) {
        toast("Informe o nome");
        return;
      }
      try {
        if (b) await Store.donoAtualizarBarbeiro(b.id, body);
        else await Store.donoCriarBarbeiro(body);
        await Store.syncCatalogo().catch(() => {});
        toast("Barbeiro salvo");
        renderPainelBarbeiros();
      } catch (e) {
        toast(e.message || "Erro");
      }
    });
  }

  async function renderPainelServicos() {
    const box = $("#painel-conteudo");
    box.innerHTML = `<p class="carregando-slots">Carregando…</p>`;
    try {
      const lista = await Store.donoServicos();
      box.innerHTML = `
        <button type="button" class="btn-painel-add" id="btn-novo-servico">+ Novo serviço</button>
        <div id="form-servico" class="form-painel oculto"></div>
        ${lista
          .map(
            (s) => `
          <article class="card-painel-item">
            <div class="card-painel-item-info">
              <strong>${s.nome}</strong>
              <span>${s.duracaoMin} min · ${U().dinheiro(Number(s.preco))}</span>
              <span class="${s.ativo ? "tag-ativo" : "tag-inativo"}">${s.ativo ? "Ativo" : "Inativo"}</span>
            </div>
            <button type="button" class="btn-editar" data-id="${s.id}">Editar</button>
          </article>`
          )
          .join("")}`;
      $("#btn-novo-servico")?.addEventListener("click", () => mostrarFormServico());
      $$(".btn-editar", box).forEach((btn) => {
        btn.addEventListener("click", () => {
          const s = lista.find((x) => Number(x.id) === Number(btn.dataset.id));
          mostrarFormServico(s);
        });
      });
    } catch (e) {
      box.innerHTML = `<p class="lista-vazia erro">${e.message || "Erro"}</p>`;
    }
  }

  function mostrarFormServico(s) {
    const form = $("#form-servico");
    form.classList.remove("oculto");
    form.innerHTML = `
      <h4>${s ? "Editar serviço" : "Novo serviço"}</h4>
      <label>Nome<input type="text" id="fs-nome" value="${s?.nome || ""}" /></label>
      <label>Preço (R$)<input type="number" id="fs-preco" min="0" step="0.01" value="${s?.preco ?? ""}" /></label>
      <label>Duração (min)<input type="number" id="fs-duracao" min="15" step="15" value="${s?.duracaoMin ?? 30}" /></label>
      <label class="check-label"><input type="checkbox" id="fs-ativo" ${s?.ativo !== false ? "checked" : ""} /> Ativo</label>
      <div class="form-painel-acoes">
        <button type="button" class="btn btn-outline" id="fs-cancelar">Cancelar</button>
        <button type="button" class="btn btn-solid" id="fs-salvar">Salvar</button>
      </div>`;

    $("#fs-cancelar")?.addEventListener("click", () => form.classList.add("oculto"));
    $("#fs-salvar")?.addEventListener("click", async () => {
      const body = {
        nome: $("#fs-nome").value.trim(),
        preco: Number($("#fs-preco").value),
        duracaoMin: Number($("#fs-duracao").value),
        ativo: $("#fs-ativo").checked,
      };
      if (!body.nome || body.preco < 0 || body.duracaoMin < 15) {
        toast("Preencha os campos corretamente");
        return;
      }
      try {
        if (s) await Store.donoAtualizarServico(s.id, body);
        else await Store.donoCriarServico(body);
        await Store.syncCatalogo().catch(() => {});
        toast("Serviço salvo");
        renderPainelServicos();
      } catch (e) {
        toast(e.message || "Erro");
      }
    });
  }

  /* ---------- loja settings ---------- */

  async function renderPainelLoja() {
    const box = $("#painel-conteudo");
    box.innerHTML = `<p class="carregando-slots">Carregando…</p>`;
    try {
      const loja = await Store.donoLoja();
      let logoData = loja.logoData || null;
      box.innerHTML = `
        <div class="form-painel form-loja">
          <h4>Dados da loja</h4>
          <label>Nome
            <input type="text" id="fl-nome" value="${loja.nome || ""}" maxlength="120" />
          </label>
          <label>Telefone
            <input type="tel" id="fl-telefone" value="${loja.telefone || ""}" maxlength="30" />
          </label>
          <label>Link público
            <input type="text" value="#/loja/${loja.slug || ""}" readonly />
          </label>
          <label>Logo
            <input type="file" id="fl-logo" accept="image/*" />
            <div id="fl-logo-preview" class="foto-preview ${logoData ? "" : "oculto"}">
              ${logoData ? `<img src="${logoData}" alt="" />` : ""}
            </div>
          </label>
          <p class="resumo-nota">Plano: ${loja.plano || "—"} · Assinatura: ${loja.statusAssinatura || "—"}</p>
          <div class="form-painel-acoes">
            <button type="button" class="btn btn-solid" id="fl-salvar">Salvar loja</button>
          </div>
        </div>`;

      $("#fl-logo")?.addEventListener("change", async (e) => {
        const file = e.target.files?.[0];
        if (!file) return;
        try {
          logoData = await U().comprimirImagem(file, 400, 0.7);
          const prev = $("#fl-logo-preview");
          if (prev) {
            prev.classList.remove("oculto");
            prev.innerHTML = `<img src="${logoData}" alt="" />`;
          }
        } catch {
          toast("Não foi possível processar o logo");
        }
      });

      $("#fl-salvar")?.addEventListener("click", async () => {
        const nome = $("#fl-nome").value.trim();
        if (!nome) return toast("Informe o nome");
        try {
          await Store.donoAtualizarLoja({
            nome,
            telefone: $("#fl-telefone").value.trim(),
            logoData,
          });
          atualizarMarcasUI();
          toast("Loja atualizada");
        } catch (e) {
          toast(e.message || "Erro ao salvar");
        }
      });
    } catch (e) {
      box.innerHTML = `<p class="lista-vazia erro">${e.message || "Erro"}</p>`;
    }
  }

  /* ---------- bloqueios: draft local + sync no salvar ---------- */

  function draftKeyBloqueios() {
    const dataIso = U().toISODate(bloqueioDataSel);
    return `laminapro_bl_${Store.getSlug()}_${bloqueioBarbeiroId}_${dataIso}`;
  }

  function salvarDraftBloqueiosLocal() {
    try {
      localStorage.setItem(
        draftKeyBloqueios(),
        JSON.stringify({
          horas: [...bloqueiosDraftSet],
          dirty: bloqueiosDirty,
        })
      );
    } catch {
      /* ignore */
    }
  }

  function lerDraftBloqueiosLocal() {
    try {
      const raw = localStorage.getItem(draftKeyBloqueios());
      if (!raw) return null;
      return JSON.parse(raw);
    } catch {
      return null;
    }
  }

  function limparDraftBloqueiosLocal() {
    try {
      localStorage.removeItem(draftKeyBloqueios());
    } catch {
      /* ignore */
    }
  }

  function isBloqueiosDirty() {
    const serverHoras = new Set([...bloqueiosServerMap.keys()]);
    if (serverHoras.size !== bloqueiosDraftSet.size) return true;
    for (const h of bloqueiosDraftSet) {
      if (!serverHoras.has(h)) return true;
    }
    return false;
  }

  function atualizarBarraSalvarBloqueios() {
    let bar = $("#barra-salvar-bloqueios");
    bloqueiosDirty = isBloqueiosDirty();
    if (!bloqueiosDirty) {
      bar?.remove();
      salvarDraftBloqueiosLocal();
      return;
    }
    if (!bar) {
      bar = document.createElement("div");
      bar.id = "barra-salvar-bloqueios";
      bar.className = "barra-salvar-bloqueios";
      bar.innerHTML = `
        <span>Alterações nos horários</span>
        <button type="button" class="btn btn-solid" id="btn-salvar-bloqueios">Salvar horários</button>`;
      document.body.appendChild(bar);
      $("#btn-salvar-bloqueios")?.addEventListener("click", salvarBloqueiosSync);
    }
    salvarDraftBloqueiosLocal();
  }

  async function salvarBloqueiosSync() {
    const btn = $("#btn-salvar-bloqueios");
    if (btn) {
      btn.disabled = true;
      btn.textContent = "Salvando…";
    }
    const dataIso = U().toISODate(bloqueioDataSel);
    const criar = [];
    const removerIds = [];

    bloqueiosDraftSet.forEach((hora) => {
      if (!bloqueiosServerMap.has(hora)) {
        criar.push({
          hora: hora.length === 5 ? hora + ":00" : hora,
          motivo: "Bloqueado pelo profissional",
        });
      }
    });
    bloqueiosServerMap.forEach((bl, hora) => {
      if (!bloqueiosDraftSet.has(hora) && bl.id != null) {
        removerIds.push(Number(bl.id));
      }
    });

    try {
      const lista = await Store.donoSyncBloqueios({
        data: dataIso,
        barbeiroId: Number(bloqueioBarbeiroId),
        bloqueios: criar,
        removerIds,
      });
      bloqueiosServerMap = new Map();
      (lista || [])
        .filter(
          (bl) =>
            bl.barbeiroId == null ||
            Number(bl.barbeiroId) === Number(bloqueioBarbeiroId)
        )
        .forEach((bl) => {
          const h = String(bl.hora).slice(0, 5);
          bloqueiosServerMap.set(h, bl);
        });
      bloqueiosDraftSet = new Set(bloqueiosServerMap.keys());
      bloqueiosDirty = false;
      limparDraftBloqueiosLocal();
      $("#barra-salvar-bloqueios")?.remove();
      toast("Horários salvos");
      renderGradeBloqueioSlots();
    } catch (e) {
      toast(e.message || "Erro ao salvar");
      if (btn) {
        btn.disabled = false;
        btn.textContent = "Salvar horários";
      }
    }
  }

  async function renderPainelBloqueios() {
    const box = $("#painel-conteudo");
    box.innerHTML = `<p class="carregando-slots">Carregando…</p>`;
    $("#barra-salvar-bloqueios")?.remove();

    if (!bloqueioBarbeiroId) {
      await renderBloqueioEscolherBarbeiro(box);
      return;
    }
    await renderBloqueioGrade(box);
  }

  async function renderBloqueioEscolherBarbeiro(box) {
    try {
      const lista = await Store.donoBarbeiros();
      const ativos = lista.filter((b) => b.ativo !== false);
      box.innerHTML = `
        <p class="bloqueio-dica">Escolha o profissional. Toque nos horários para marcar/desmarcar — salve quando terminar.</p>
        <div class="lista-barbeiros-bloqueio">
          ${ativos
            .map(
              (b) => `
            <button type="button" class="card-barbeiro-pick" data-id="${b.id}">
              ${avatarHtml(b)}
              <strong>${b.nome}</strong>
              <span>Gerenciar horários →</span>
            </button>`
            )
            .join("")}
        </div>`;
      $$(".card-barbeiro-pick", box).forEach((btn) => {
        btn.addEventListener("click", () => {
          bloqueioBarbeiroId = Number(btn.dataset.id);
          bloqueioDataSel = new Date();
          bloqueioCalCursor = new Date();
          renderPainelBloqueios();
        });
      });
    } catch (e) {
      box.innerHTML = `<p class="lista-vazia erro">${e.message || "Erro"}</p>`;
    }
  }

  async function renderBloqueioGrade(box) {
    const barbeiros = Store.getBarbeiros();
    let barb = barbeiros.find((b) => Number(b.id) === Number(bloqueioBarbeiroId));
    if (!barb) {
      try {
        const lista = await Store.donoBarbeiros();
        barb = lista.find((b) => Number(b.id) === Number(bloqueioBarbeiroId));
      } catch (_) {}
    }
    if (!barb) {
      bloqueioBarbeiroId = null;
      return renderPainelBloqueios();
    }

    const { mes, ano } = U().mesAno(bloqueioCalCursor);
    box.innerHTML = `
      <button type="button" class="btn-voltar-barbeiro" id="bl-voltar-barbeiro">← Trocar profissional</button>
      <div class="bloqueio-header-barb">
        ${avatarHtml(barb)}
        <div>
          <strong>${barb.nome}</strong>
          <span>Toque para bloquear / liberar (salve depois)</span>
        </div>
      </div>
      <div class="cal-wrap bloqueio-cal">
        <div class="cal-topo">
          <button type="button" class="cal-nav" id="bl-cal-prev" aria-label="Semana anterior">‹</button>
          <strong id="bl-cal-mes">${mes}</strong>
          <span class="ano" id="bl-cal-ano">${ano}</span>
          <button type="button" class="cal-nav" id="bl-cal-next" aria-label="Próxima semana">›</button>
          <button type="button" class="btn-hoje" id="bl-cal-hoje">Hoje</button>
        </div>
        <div id="bl-cal-dias" class="cal-dias-row"></div>
      </div>
      <p class="bloqueio-legenda"><span class="leg-livre"></span> Livre <span class="leg-bloq"></span> Bloqueado <span class="leg-ocup"></span> Já agendado</p>
      <div id="bl-grade-slots" class="grade-slots bloqueio-grade">
        <p class="carregando-slots">Carregando horários…</p>
      </div>`;

    $("#bl-voltar-barbeiro")?.addEventListener("click", () => {
      if (bloqueiosDirty && !confirm("Há alterações não salvas. Descartar?")) return;
      bloqueioBarbeiroId = null;
      $("#barra-salvar-bloqueios")?.remove();
      renderPainelBloqueios();
    });
    $("#bl-cal-prev")?.addEventListener("click", () => {
      if (bloqueiosDirty && !confirm("Há alterações não salvas. Descartar?")) return;
      bloqueioDataSel = new Date(bloqueioDataSel);
      bloqueioDataSel.setDate(bloqueioDataSel.getDate() - 7);
      bloqueioCalCursor = new Date(bloqueioDataSel);
      renderPainelBloqueios();
    });
    $("#bl-cal-next")?.addEventListener("click", () => {
      if (bloqueiosDirty && !confirm("Há alterações não salvas. Descartar?")) return;
      bloqueioDataSel = new Date(bloqueioDataSel);
      bloqueioDataSel.setDate(bloqueioDataSel.getDate() + 7);
      bloqueioCalCursor = new Date(bloqueioDataSel);
      renderPainelBloqueios();
    });
    $("#bl-cal-hoje")?.addEventListener("click", () => {
      if (bloqueiosDirty && !confirm("Há alterações não salvas. Descartar?")) return;
      bloqueioDataSel = new Date();
      bloqueioCalCursor = new Date();
      renderPainelBloqueios();
    });

    renderBloqueioCalendario();
    await carregarBloqueioSlots();
  }

  function renderBloqueioCalendario() {
    const dias = $("#bl-cal-dias");
    if (!dias) return;
    const { mes, ano } = U().mesAno(bloqueioCalCursor);
    const mesEl = $("#bl-cal-mes");
    const anoEl = $("#bl-cal-ano");
    if (mesEl) mesEl.textContent = mes;
    if (anoEl) anoEl.textContent = String(ano);

    const ref = new Date(bloqueioDataSel);
    const domingo = new Date(ref);
    domingo.setDate(ref.getDate() - ref.getDay());

    const hoje = new Date();
    hoje.setHours(0, 0, 0, 0);

    dias.innerHTML = "";
    for (let i = 0; i < 7; i++) {
      const d = new Date(domingo);
      d.setDate(domingo.getDate() + i);
      const selecionado = U().isMesmoDia(d, bloqueioDataSel);
      const ehHoje = U().isMesmoDia(d, hoje);
      const funciona = B().agenda.diasFuncionamento.includes(d.getDay());

      const btn = document.createElement("button");
      btn.type = "button";
      btn.className =
        "cal-dia" +
        (selecionado ? " selecionado" : "") +
        (ehHoje ? " hoje" : "") +
        (!funciona ? " disabled" : "");
      btn.disabled = !funciona;
      btn.innerHTML = `<span>${U().diaSemanaLetra(d)}</span><strong>${d.getDate()}</strong>`;
      btn.addEventListener("click", () => {
        if (bloqueiosDirty && !confirm("Há alterações não salvas. Descartar?")) return;
        bloqueioDataSel = d;
        bloqueioCalCursor = new Date(d);
        renderBloqueioCalendario();
        carregarBloqueioSlots();
      });
      dias.appendChild(btn);
    }
  }

  async function carregarBloqueioSlots() {
    const grade = $("#bl-grade-slots");
    if (!grade || !bloqueioBarbeiroId) return;
    const dataIso = U().toISODate(bloqueioDataSel);
    grade.innerHTML = `<p class="carregando-slots">Carregando…</p>`;
    bloqueiosSlugDraftKey = draftKeyBloqueios();

    try {
      const [bloqueios, agendaDono] = await Promise.all([
        Store.donoBloqueios(dataIso),
        Store.donoAgendamentos(60, 0).catch(() => []),
      ]);

      bloqueiosServerMap = new Map();
      (bloqueios || [])
        .filter(
          (bl) =>
            bl.barbeiroId == null ||
            Number(bl.barbeiroId) === Number(bloqueioBarbeiroId)
        )
        .forEach((bl) => {
          bloqueiosServerMap.set(String(bl.hora).slice(0, 5), bl);
        });

      const draftLocal = lerDraftBloqueiosLocal();
      if (draftLocal?.dirty && Array.isArray(draftLocal.horas)) {
        bloqueiosDraftSet = new Set(draftLocal.horas);
        bloqueiosDirty = true;
      } else {
        bloqueiosDraftSet = new Set(bloqueiosServerMap.keys());
        bloqueiosDirty = false;
      }

      bloqueiosOcupados = new Set(
        (agendaDono || [])
          .filter(
            (ag) =>
              ag.data === dataIso &&
              Number(ag.barbeiroId) === Number(bloqueioBarbeiroId)
          )
          .map((ag) => String(ag.hora).slice(0, 5))
      );

      renderGradeBloqueioSlots();
      atualizarBarraSalvarBloqueios();
    } catch (e) {
      grade.innerHTML = `<p class="lista-vazia erro">${e.message || "Erro ao carregar"}</p>`;
    }
  }

  function renderGradeBloqueioSlots() {
    const grade = $("#bl-grade-slots");
    if (!grade) return;
    const slots = U().gerarSlots();
    grade.innerHTML = slots
      .map((hora) => {
        const ocupado = bloqueiosOcupados.has(hora);
        if (ocupado) {
          return `<button type="button" class="slot slot-ocupado" disabled title="Já agendado">${hora}</button>`;
        }
        const bloq = bloqueiosDraftSet.has(hora);
        if (bloq) {
          return `<button type="button" class="slot slot-bloqueado" data-hora="${hora}" title="Clique para liberar">${hora}</button>`;
        }
        return `<button type="button" class="slot slot-livre" data-hora="${hora}" title="Clique para bloquear">${hora}</button>`;
      })
      .join("");

    $$(".slot-livre, .slot-bloqueado", grade).forEach((btn) => {
      btn.addEventListener("click", () => toggleBloqueioSlotLocal(btn.dataset.hora));
    });
  }

  function toggleBloqueioSlotLocal(hora) {
    if (bloqueiosDraftSet.has(hora)) bloqueiosDraftSet.delete(hora);
    else bloqueiosDraftSet.add(hora);
    renderGradeBloqueioSlots();
    atualizarBarraSalvarBloqueios();
  }

  /* ---------- assinatura / planos ---------- */

  async function renderPainelPlanos() {
    const box = $("#painel-conteudo");
    box.innerHTML = `<p class="carregando-slots">Carregando planos…</p>`;
    try {
      const [status, catalogo] = await Promise.all([
        Store.donoAssinaturaStatus().catch(() => null),
        Store.donoAssinaturaCatalogo(),
      ]);
      const faixas = agruparCatalogoPorFaixa(catalogo || []);
      const st = status || {};
      box.innerHTML = `
        <div class="planos-status">
          <strong>${st.planoRotulo || "Trial"}</strong>
          <span class="badge-status ${st.ativa ? "finalizado" : "confirmado"}">${st.bloqueada ? "Bloqueado" : st.ativa ? "Ativo" : st.status || "Trial"}</span>
          <p>Até <b>${st.maxBarbeiros ?? 2}</b> barbeiro(s)${
            st.bloqueada
              ? " · assine para liberar os recursos"
              : st.trialExpiraEmTexto
                ? " · trial até " + st.trialExpiraEmTexto
                : st.expiraEmTexto
                  ? " · válido até " + st.expiraEmTexto
                  : ""
          }</p>
          ${st.bloqueada ? `<p class="paywall-inline">Seu teste acabou. Escolha um plano abaixo para continuar.</p>` : ""}
        </div>
        <div class="planos-lista">
          ${faixas
            .map(
              (f) => `
            <article class="plano-faixa">
              <header>
                <h3>${f.equipe}</h3>
                <span>até ${f.maxBarbeiros} profissionais</span>
              </header>
              <div class="plano-periodos">
                ${f.itens
                  .map(
                    (p) => `
                  <button type="button" class="btn-plano" data-plano-id="${p.id}">
                    <strong>${periodoRotulo(p.periodo)}</strong>
                    <span>${p.preco}</span>
                    <small>${p.dica || ""}</small>
                  </button>`
                  )
                  .join("")}
              </div>
            </article>`
            )
            .join("")}
        </div>
        ${!st.checkoutWeb ? `<p class="lista-vazia">Pagamento web ainda não ligado (faltam as chaves do Mercado Pago).</p>` : ""}`;

      $$(".btn-plano", box).forEach((btn) => {
        btn.addEventListener("click", () => iniciarCheckoutAssinatura(btn.dataset.planoId, btn));
      });
    } catch (e) {
      box.innerHTML = `<p class="lista-vazia erro">${e.message || "Erro ao carregar planos"}</p>`;
    }
  }

  function agruparCatalogoPorFaixa(lista) {
    const map = new Map();
    for (const p of lista) {
      const key = p.faixa || p.equipe;
      if (!map.has(key)) {
        map.set(key, { equipe: p.equipe, maxBarbeiros: p.maxBarbeiros, itens: [] });
      }
      map.get(key).itens.push(p);
    }
    return [...map.values()];
  }

  function periodoRotulo(p) {
    if (p === "M") return "Mensal";
    if (p === "S") return "Semestral (−15%)";
    if (p === "A") return "Anual (−30%)";
    return p || "Plano";
  }

  async function iniciarCheckoutAssinatura(planoId, btn) {
    const id = planoId || "p_1_2_m";
    const original = btn?.textContent;
    if (btn) {
      btn.disabled = true;
      btn.textContent = "Abrindo…";
    }
    try {
      toast("Abrindo checkout Mercado Pago…");
      const res = await Store.donoCheckoutAssinatura(id);
      const url = res.initPoint || res.init_point || res.sandboxInitPoint;
      if (!url) {
        toast(res.message || "Checkout indisponível no momento");
        return;
      }
      window.location.href = url;
    } catch (e) {
      toast(e.message || "Erro ao iniciar assinatura");
    } finally {
      if (btn) {
        btn.disabled = false;
        if (original) btn.innerHTML = original;
      }
    }
  }

  /* ---------- Google Sign-In ---------- */

  async function aposAuth(usuario, silencioso = false) {
    if (!silencioso) toast("Bem-vindo!");
    if (usuario.papel === "DONO") {
      Store.setUltimoModo("dono");
      if (usuario.slug) Store.setLoja(usuario.slug);
      setHash("/dono");
      await entrarApp({ modo: "dono", tab: "painel" });
      return;
    }
    Store.setUltimoModo("cliente");
    if (Store.getSlug() || rotaAtual.slug) {
      const slug = Store.getSlug() || rotaAtual.slug;
      setHash(`/loja/${slug}`);
      await entrarApp({ modo: "loja" });
      return;
    }
    setHash("/");
    mostrarEntry();
  }

  async function handleGoogleCredential(response) {
    try {
      if (!response?.credential) {
        toast("Login Google cancelado");
        return;
      }
      const usuario = await Store.loginGoogle(response.credential);
      await aposAuth(usuario);
    } catch (err) {
      toast(err.message || "Erro no login Google");
    }
  }

  function montarBotaoGoogle() {
    const el = $("#google-btn");
    if (!el || !window.google?.accounts?.id) return;
    el.innerHTML = "";
    const largura = Math.min(360, Math.max(240, el.parentElement?.clientWidth || 320));
    google.accounts.id.renderButton(el, {
      theme: "outline",
      size: "large",
      width: largura,
      text: "continue_with",
      shape: "rectangular",
      logo_alignment: "left",
      locale: "pt-BR",
    });
  }

  function initGoogleSignIn(tentativas = 0) {
    if (googlePronto) {
      montarBotaoGoogle();
      return;
    }
    if (!window.google?.accounts?.id) {
      if (tentativas < 40) {
        setTimeout(() => initGoogleSignIn(tentativas + 1), 150);
      }
      return;
    }
    google.accounts.id.initialize({
      client_id: GOOGLE_CLIENT_ID,
      callback: handleGoogleCredential,
      auto_select: true,
      cancel_on_tap_outside: true,
      itp_support: true,
    });
    googlePronto = true;
    montarBotaoGoogle();
  }

  function tentarGoogleOneTap() {
    if (!googlePronto || Store.temAuthReal()) return;
    if (rotaAtual.tipo !== "entry" && rotaAtual.tipo !== "login") return;
    try {
      google.accounts.id.prompt();
    } catch {
      /* ignore */
    }
  }

  /* ---------- criar loja / slug preview ---------- */

  function atualizarSlugPreview() {
    const nome = $("#cl-barbearia")?.value || "";
    const manual = $("#cl-slug")?.value?.trim();
    const slug = slugAutoEditado ? U().slugify(nome) : U().slugify(manual || nome);
    if (slugAutoEditado && $("#cl-slug")) {
      $("#cl-slug").value = slug;
    }
    const prev = $("#cl-slug-preview");
    if (prev) prev.textContent = slug || "—";
  }

  /* ---------- boot ---------- */

  function bind() {
    $("#btn-entry-dono")?.addEventListener("click", async () => {
      Store.setUltimoModo("dono");
      const u = await garantirSessao();
      if (u?.papel === "DONO" && Store.temAuthReal()) {
        await aposAuth(u, true);
        return;
      }
      mostrarLogin({ intent: "dono" });
    });
    $("#btn-entry-criar")?.addEventListener("click", () => {
      setHash("/criar-loja");
    });
    $("#btn-entry-ir")?.addEventListener("click", () => {
      const slug = $("#entry-slug")?.value?.trim();
      entrarLoja(slug);
    });
    $("#entry-slug")?.addEventListener("keydown", (e) => {
      if (e.key === "Enter") {
        e.preventDefault();
        entrarLoja($("#entry-slug").value.trim());
      }
    });
    $("#btn-entry-demo")?.addEventListener("click", () => entrarLoja("demo"));

    $("#btn-criar-voltar")?.addEventListener("click", () => setHash("/"));
    $("#btn-login-voltar")?.addEventListener("click", () => setHash("/"));

    $("#cl-barbearia")?.addEventListener("input", () => {
      if (slugAutoEditado) atualizarSlugPreview();
    });
    $("#cl-slug")?.addEventListener("input", () => {
      slugAutoEditado = false;
      atualizarSlugPreview();
    });

    $("#form-criar-loja")?.addEventListener("submit", async (e) => {
      e.preventDefault();
      const nome = $("#cl-nome").value.trim();
      const email = $("#cl-email").value.trim();
      const senha = $("#cl-senha").value;
      const nomeBarbearia = $("#cl-barbearia").value.trim();
      const slug = U().slugify($("#cl-slug").value.trim() || nomeBarbearia);
      const telefone = $("#cl-telefone").value.trim();
      if (!nome || !email || !senha || !nomeBarbearia) {
        toast("Preencha os campos obrigatórios");
        return;
      }
      if (senha.length < 6) {
        toast("Senha deve ter no mínimo 6 caracteres");
        return;
      }
      const btn = $("#btn-criar-submit");
      if (btn) {
        btn.disabled = true;
        btn.textContent = "Criando…";
      }
      try {
        const usuario = await Store.criarLoja({
          nome,
          email,
          senha,
          nomeBarbearia,
          slug,
          telefone,
        });
        toast("Barbearia criada!");
        setHash("/");
        await entrarApp({ modo: "dono", tab: "painel" });
        void usuario;
      } catch (err) {
        toast(err.message || "Erro ao criar loja");
      } finally {
        if (btn) {
          btn.disabled = false;
          btn.textContent = "Criar e entrar";
        }
      }
    });

    $("#form-login")?.addEventListener("submit", async (e) => {
      e.preventDefault();
      const email = $("#login-email").value.trim();
      const senha = $("#login-senha").value;
      if (!email || !senha) {
        toast("Informe e-mail e senha");
        return;
      }
      try {
        const usuario = await Store.loginApi(email, senha);
        await aposAuth(usuario);
      } catch (err) {
        toast(err.message || "Erro no login");
      }
    });

    $("#form-cadastro")?.addEventListener("submit", async (e) => {
      e.preventDefault();
      const nome = $("#cad-nome").value.trim();
      const email = $("#cad-email").value.trim();
      const senha = $("#cad-senha").value;
      if (!nome || !email || !senha) {
        toast("Preencha todos os campos");
        return;
      }
      try {
        const usuario = await Store.cadastroApi(nome, email, senha);
        await aposAuth(usuario);
      } catch (err) {
        toast(err.message || "Erro no cadastro");
      }
    });

    $("#btn-auth-cadastro")?.addEventListener("click", () => alternarModoAuth(true));
    $("#btn-auth-login")?.addEventListener("click", () => alternarModoAuth(false));

    $$(".tab").forEach((t) =>
      t.addEventListener("click", () => ativarTab(t.dataset.tab))
    );

    $("#btn-agendar")?.addEventListener("click", abrirWizard);
    $$(".btn-fechar-wizard").forEach((b) =>
      b.addEventListener("click", fecharWizard)
    );

    $$("[data-path]").forEach((btn) => {
      btn.addEventListener("click", () => {
        booking.path = btn.dataset.path;
        salvarDraft();
        if (booking.path === "servico") irPasso("servicos");
        else irPasso("barbeiro");
      });
    });

    $("#busca-servico")?.addEventListener("input", renderServicos);
    $("#btn-wizard-voltar")?.addEventListener("click", voltarPasso);
    $("#btn-wizard-proximo")?.addEventListener("click", avancarPasso);
    $("#btn-wizard-agendar")?.addEventListener("click", confirmarAgendamento);

    $("#btn-remover-servico")?.addEventListener("click", () => {
      booking.servicoId = null;
      irPasso("servicos");
    });

    $("#cal-prev")?.addEventListener("click", async () => {
      calCursor.setMonth(calCursor.getMonth() - 1);
      renderCalendario();
      await renderSlots();
    });
    $("#cal-next")?.addEventListener("click", async () => {
      calCursor.setMonth(calCursor.getMonth() + 1);
      renderCalendario();
      await renderSlots();
    });
    $("#cal-hoje")?.addEventListener("click", async () => {
      const hoje = new Date();
      calCursor = new Date(hoje);
      dataSelecionada = new Date(hoje);
      booking.data = U().toISODate(hoje);
      booking.hora = null;
      salvarDraft();
      renderCalendario();
      await renderSlots();
      atualizarBtnProximo();
    });

    $("#btn-succ-agenda")?.addEventListener("click", () => {
      fecharWizard();
      ativarTab("agenda");
    });

    $("#btn-sair")?.addEventListener("click", () => {
      if (confirm("Deseja sair?")) {
        Store.logout();
        limparLembretes();
        $("#barra-salvar-bloqueios")?.remove();
        setHash("/");
        mostrarEntry();
        initGoogleSignIn();
      }
    });

    $("#btn-painel-opcoes")?.addEventListener("click", () => ativarTab("painel"));
    $("#btn-assinatura")?.addEventListener("click", () => {
      ativarTab("painel");
      painelSecao = "planos";
      renderPainel();
    });
    $("#btn-relatar")?.addEventListener("click", () => {
      toast("Obrigado! Em breve abriremos o canal de suporte.");
    });

    $$(".painel-nav-btn").forEach((btn) => {
      btn.addEventListener("click", () => {
        painelSecao = btn.dataset.secao;
        renderPainel();
      });
    });

    $("#conf-obs")?.addEventListener("input", (e) => {
      booking.observacao = e.target.value;
      salvarDraft();
    });

    window.addEventListener("hashchange", () => {
      onHashChange();
    });
  }

  async function init() {
    bind();
    restaurarDraft();
    initGoogleSignIn();
    await Store.restaurarSessao();
    await onHashChange();
    tentarGoogleOneTap();

    const splash = $("#splash");
    if (splash) {
      setTimeout(() => {
        splash.classList.add("splash-sair");
        setTimeout(() => splash.remove(), 420);
      }, 900);
    }
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
