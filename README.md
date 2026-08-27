# Encaixe

SaaS web mobile-first de agendamento para barbearias.

**Slogan:** Agenda que encaixa.

Cada barbearia tem seu link (`#/loja/{slug}`), dados isolados, painel do dono e assinatura Solo (R$ 49,90) via Mercado Pago.

## Links

- Site (GitHub Pages): https://eukevytosdev.github.io/Barberini/
- API (Render): https://barberini-api.onrender.com — ver [DEPLOY.md](DEPLOY.md)

> O repositório ainda se chama Barberini; o **produto** é Encaixe.

## Como rodar

### Backend (Java 17)

```bash
cd backend
export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home
./mvnw spring-boot:run
```

Porta **8080**. H2 local em `backend/data/`.

### Frontend

```bash
cd frontend
python3 -m http.server 5173 --bind 127.0.0.1
```

Abra: http://127.0.0.1:5173/

## Fluxos principais

| Rota | Quem | O que faz |
|------|------|-----------|
| `#/` | todos | Gate: criar loja / entrar / abrir por slug |
| `#/criar-loja` | dono | Onboarding + cria barbearia |
| `#/loja/{slug}` | cliente | Agendar (sync só ao confirmar) |
| Painel | dono | Resumo, agenda, barbeiros (com foto), serviços, horários (salvar em lote), loja, assinatura |

## Credenciais demo (seed)

| Campo | Valor |
|-------|-------|
| E-mail | `dono@barberini.com` |
| Senha | `dono123` |
| Loja | `#/loja/demo` |

## Sync-on-save

- **Cliente:** rascunho no `localStorage`; só `POST /api/agendamentos` ao clicar Agendar.
- **Dono (bloqueios):** toques ficam no draft local; só sobem no servidor ao clicar **Salvar horários**.

## Mercado Pago

Variáveis: `MERCADOPAGO_ACCESS_TOKEN`, `MERCADOPAGO_PUBLIC_KEY`.  
Sem token, o checkout responde em modo sandbox (útil em dev).

## Estrutura

```text
Barberini/   # repo
├── backend/          # Spring Boot API (multi-tenant)
├── frontend/         # Encaixe web app
├── DEPLOY.md
└── README.md
```
