# Deploy — Lâmina Pro

## Já no ar

| Peça | Onde |
|------|------|
| Front | https://eukevytosdev.github.io/LaminaPro/ |
| Repo | https://github.com/euKevytosDev/LaminaPro |
| Banco | Neon project `barberini` (`icy-surf-10621417`) |
| API (preferida) | **Northflank** — `laminapro-api` |
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
```

### Front apontando pra API Northflank

```js
localStorage.setItem("encaixe_api_url", "https://p01--laminapro-api--w5zz78kgqjtj.code.run");
location.reload();
```

## Login dono (seed)

- Novo: `dono@laminapro.app` / `dono123`
- Antigo (Neon): `dono@barberini.com` / `dono123`
- Loja demo: `#/loja/demo`

## Login Google

Authorized JavaScript origins:

- `https://eukevytosdev.github.io`
- `http://localhost:5173`

## Local

- Front: `python3 -m http.server 5173` em `frontend/`
- Back: Java 17 · perfil `local` (H2)
