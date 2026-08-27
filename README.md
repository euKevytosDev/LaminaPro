# Lâmina Pro

SaaS web mobile-first de agendamento para barbearias.

**Repo:** https://github.com/euKevytosDev/LaminaPro  
**Site:** https://eukevytosdev.github.io/LaminaPro/  
**API:** Northflank (`laminapro-api`) — ver [DEPLOY.md](DEPLOY.md)

> O produto se chama **Lâmina Pro**. O pacote Java interno ainda usa `br.com.barberini` (só técnico).

## Como rodar

### Backend (Java 17)

```bash
cd backend
export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home
./mvnw spring-boot:run
```

### Frontend

```bash
cd frontend
python3 -m http.server 5173 --bind 127.0.0.1
```

Abra: http://127.0.0.1:5173/

## Credenciais demo (seed)

| Campo | Valor |
|-------|-------|
| E-mail | `dono@laminapro.app` (ou `dono@barberini.com` no Neon antigo) |
| Senha | `dono123` |
| Loja | `#/loja/demo` |

## Sync-on-save

- **Cliente:** rascunho no `localStorage`; sync ao clicar Agendar.
- **Dono (bloqueios):** draft local; sync ao clicar **Salvar horários**.

## Mercado Pago

`MERCADOPAGO_ACCESS_TOKEN` e `MERCADOPAGO_PUBLIC_KEY` no host da API.
