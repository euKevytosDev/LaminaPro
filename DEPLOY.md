# Deploy — Encaixe

## Já no ar

| Peça | Onde |
|------|------|
| Front | https://eukevytosdev.github.io/Barberini/ |
| Banco | Neon project `barberini` (`icy-surf-10621417`) |
| API (preferida) | **Northflank** — ver abaixo |
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

Plano Sandbox free: compute always-on (sem cold start de 15 min).  
Se um dia precisar, o upgrade pago (~US$ 5) é o próximo passo.

### Passo a passo

1. Crie conta em https://app.northflank.com e um **Project** (ex.: `encaixe`).
2. **Add service** → **Combined service** (build + deploy) ou **Deployment** from GitHub.
3. Conecte o repo `euKevytosDev/Barberini`.
4. Build:
   - **Dockerfile path:** `backend/Dockerfile`
   - **Build context:** `backend`
5. Port: **8080** (ou a que o painel injetar em `PORT`).
6. Health check: `GET /api/health` (path `/api/health`).
7. Environment — copie de `.env.northflank.example` e preencha Neon + `APP_JWT_SECRET`.
8. Deploy e copie a URL pública (ex.: `https://encaixe-api-….northflank.app`).

### Front apontando pra Northflank

No navegador (Pages), abra o console uma vez:

```js
localStorage.setItem("encaixe_api_url", "https://SUA-URL.northflank.app");
location.reload();
```

Ou altere o fallback em `frontend/js/api.js` (`API_BASE_PROD`) e faça push (Pages redeploya).

### UptimeRobot (opcional na Northflank)

No free always-on **não precisa** ping pra evitar cold start.  
Se quiser monitoramento: `GET https://SUA-URL/api/health` a cada 5–10 min (timeout 30s).

## Login dono (seed)

- E-mail: `dono@barberini.com`
- Senha: `dono123`
- Loja demo: `#/loja/demo`

## Login Google

Authorized JavaScript origins:

- `https://eukevytosdev.github.io`
- `http://localhost:5173`

## Local

- Front: `python3 -m http.server 5173` em `frontend/` → API `localhost:8080`
- Back: Java 17 · perfil `local` (H2)
