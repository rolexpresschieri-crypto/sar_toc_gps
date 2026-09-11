# TOC SAR — backend web

Next.js per la sala operativa **TOC SAR**. Stesso Supabase dell’app (non gestSQUADRE).

- Enti: solo tu, in Supabase Table Editor (`organizations`, `is_enabled`)
- Login TOC: **ente + codice admin + password** (come l’app)
- Home `/`: sala operativa (operatori online, notifiche, mappa)
- **LOG** `/log`: notifiche, foto (apri/salva JPEG) e login operatori
- **Config** `/config`: operatori online, visibilità in mappa verso gli altri, log-out forzato
- **Mappa su schermo grande**: nuova finestra da trascinare sul secondo monitor (`/map-fullscreen`)
- Anagrafica `/anagrafica`: cartelle associazione, operatori e operazioni. Tabella DB operazioni: `events`

## Avvio locale

1. Copia `.env.example` → `.env.local` (URL + anon key TOC SAR).
2. Su Supabase esegui `sql/toc_admins_organization.sql` (dopo `organizations.sql`).
3. `npm install` poi `npm run dev` oppure `start-toc.bat`
4. http://localhost:3000 — es. **NVANSMI** / **ADMIN_RR** / **123456**

## URL pubblico

https://toc-sar.vercel.app — stesso login, senza avviare localhost.

Per aggiornare il sito dopo una modifica: nella cartella `backend_toc` lancia `vercel --prod`.
