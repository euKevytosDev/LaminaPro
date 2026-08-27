# Deploy — Lâmina Pro

## Já no ar

| Peça | Onde |
|------|------|
| Front | https://eukevytosdev.github.io/LaminaPro/ |
| Repo | https://github.com/euKevytosDev/LaminaPro |
| Banco | Neon project `barberini` (`icy-surf-10621417`) |
| API (preferida) | **Northflank** — https://p01--laminapro-api--w5zz78kgqjtj.code.run |
| API (legado) | https://barberini-api.onrender.com (Render free, dorme) |

## Neon (banco)

Projeto: **barberini** · DB: `neondb` · região us-east-2

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://ep-….aws.neon.tech/neondb?sslmode=require
SPRING_DATASOURCE_USERNAME=neondb_owner
SPRING_DATASOURCE_PASSWORD=<console Neon>
```

A URL **precisa** começar com `jdbc:postgresql://`.

## Northflank (API — recomendado)

1. Project: `laminapro` · Service: `laminapro-api`
2. Repo GitHub: `euKevytosDev/LaminaPro` (branch `main`)
3. Dockerfile: `backend/Dockerfile` · context: `backend`
4. Port `8080` · health `/api/health`
5. Envs — ver bloco abaixo / `.env.northflank.example`

### Variáveis (Lâmina Pro)

```text
SPRING_PROFILES_ACTIVE=postgres
SPRING_DATASOURCE_URL=jdbc:postgresql://HOST/neondb?sslmode=require
SPRING_DATASOURCE_USERNAME=neondb_owner
SPRING_DATASOURCE_PASSWORD=
APP_JWT_SECRET=laminapro-jwt-troque-depois-em-producao-2026
APP_PUBLIC_URL=https://eukevytosdev.github.io/LaminaPro
APP_SEED_DONO_EMAIL=dono@laminapro.app
APP_SEED_DONO_SENHA=dono123
APP_SEED_DONO_NOME=Dono LaminaPro
GOOGLE_CLIENT_ID=868389533637-d3l4a0mrnnbf7i1h34cd0mts996sb6pc.apps.googleusercontent.com
MERCADOPAGO_ACCESS_TOKEN=
APP_ASSINATURA_WEBHOOK_URL=https://p01--laminapro-api--w5zz78kgqjtj.code.run/api/assinatura/webhook
APP_ASSINATURA_TRIAL_DIAS=14
```

### Mercado Pago (assinatura da loja)

Igual ao Pelada Oficial:

1. Cole `MERCADOPAGO_ACCESS_TOKEN` nas envs da Northflank (produção).
2. No painel do MP, configure a URL de notificação:
   `https://p01--laminapro-api--w5zz78kgqjtj.code.run/api/assinatura/webhook`
3. No app: **Painel → Planos** (mensal recorrente / semestral / anual por faixa de barbeiros).
4. Após pagar, retorno: `#/dono?pago=ok`

Planos (base R$ 49,90):

| Equipe | Mensal | Semestral | Anual |
|--------|--------|-----------|-------|
| 1 a 2 | 49,90 | 254,49 | 419,16 |
| 3 a 5 | 68,90 | 351,39 | 578,76 |
| 6 a 15 | 102,90 | 524,79 | 864,36 |
| 16 a 20 | 137,90 | 703,29 | 1.158,36 |

### Front apontando pra API Northflank

```js
localStorage.setItem("laminapro_api_url", "https://p01--laminapro-api--w5zz78kgqjtj.code.run");
location.reload();
```

URL pública: `https://p01--laminapro-api--w5zz78kgqjtj.code.run`  
URL interna (cluster): `laminapro-api:8080`  
Health: `https://p01--laminapro-api--w5zz78kgqjtj.code.run/api/health`

## Login dono (seed)

- Novo: `dono@laminapro.app` / `dono123`
- Antigo (Neon): `dono@barberini.com` / `dono123`
- Loja demo: `#/loja/demo`
- Entry: **Sou dono** vs **Quero agendar** (cliente)

Ao finalizar um corte no painel: informe **valor pago** + **Pix/Dinheiro/Cartão** → o app abre o **dashboard (Resumo)**.

## Login Google

Authorized JavaScript origins:

- `https://eukevytosdev.github.io`
- `http://localhost:5173`

## Local

- Front: `python3 -m http.server 5173` em `frontend/`
- Back: Java 17 · perfil `local` (H2)
