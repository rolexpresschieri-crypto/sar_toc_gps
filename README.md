# TOC SAR

App KMP (Compose Multiplatform) per operatori SAR / unità cinofile.

- **Android** (target iniziale)
- **iOS** (Xcode + IPA Ad Hoc)

## Origine

- UI home: come `gestSQUADRE` (opzioni login, notifica, foto, GPS)
- Splash / vegetato / modulo GPS: da `TocAppBuild`
- Schema DB: copia da `gestSQUADRE/sql` in `sql/` (progetto Supabase **nuovo e dedicato**)

## Backend (come gestSQUADRE, progetto separato)

**Non riusare** URL/chiavi del Supabase di gestSQUADRE: restano due ambienti indipendenti.

1. Crea un **nuovo** progetto Supabase (es. `toc-sar`).
2. Esegui gli SQL in `sql/` **in questo ordine**:
   1. `schema_v1.sql`
   2. `map_routes.sql`
   3. `toc_mission_logs.sql`
   4. `toc_mission_force_dismiss_logs.sql`
   5. `squad_event_flow.sql`
   6. `operational_events.sql`
   7. `squad_field_photos.sql`
    8. `squad_photos_storage_policy.sql` (upload foto da app)
    9. `photo_ack.sql` (spegnimento notifica foto su TOC)
    10. `alarm_auto_notify.sql`
    11. `squad_session_auth_logs.sql` (log login/logout)
    12. `organizations.sql` (ente seed **NVANSMI**, `organization_id`)
    13. `organizations_enabled.sql` (flag `is_enabled` su enti)
    14. `organization_login.sql` (unique operatore per ente)
    15. `toc_admins_organization.sql` (admin TOC per ente: NVANSMI / ADMIN_RR / 123456)
    16. `operators_seed.sql` (opzionale: LUPO / OP001 / OP002, dopo unique per ente)
    17. `mission_gps.sql` (WP/TRK di missione per ente, al posto dello Google Sheet)
    18. `squad_track_logs.sql` (riepilogo TRK salvata: distanza, tempo, velocità, dislivello)
    19. `operator_folders.sql` (cartelle anagrafica associazione, distinte dall’ente di login)
3. Abilita Realtime sulle tabelle come in gestSQUADRE.
4. Config app: copia `supabase-config.example.json` → `supabase-config.local.json` (URL + publishable key).
5. Firebase: progetto **dedicato** TOC SAR per FCM (push).
6. Backend TOC web: cartella `backend_toc/` in questo repo (Next.js, stesso Supabase dell’app). Avvio: `backend_toc/start-toc.bat` — login **ente + admin + password** (es. **NVANSMI** / **ADMIN_RR** / **123456**). Enti solo in Supabase; TOC gestisce operatori e operazioni dell’ente.

**Terminologia:** in UI e prodotto si parla di **operatori**; nel DB le tabelle restano `squads` / `squad_sessions` (stesso protocollo gestSQUADRE). Ogni riga in `squads` = un operatore (codice, nome, password, `map_color`, `map_icon_key`).

Login app: **ente + codice operatore + password** (es. demo `NVANSMI` / `LUPO` / `1234`). L'ente è sempre maiuscolo e resta salvato sul dispositivo; «Cambia ente» per i tablet condivisi. Anagrafica enti: tabella `organizations` (solo vendor, Table Editor: `org_code` + `org_name`; `is_enabled` false = standby). Login TOC web come l’app: ente + admin (`toc_admins`) + password. Progetti già creati: `organizations.sql`, `organizations_enabled.sql`, `organization_login.sql`, `toc_admins_organization.sql`.

## Guida utente

PDF per operatori (loghi ANSMI + UCRS, dal log-in al GPS): [`docs/TOC_SAR_Guida_utente.pdf`](docs/TOC_SAR_Guida_utente.pdf).

Per rigenerarlo: `python docs/build_guida_utente.py`.

Dopo lo schema, per la visibilità pin eseguire anche `sql/peer_visible_map.sql` (se il DB non è stato creato da `schema_v1.sql` già aggiornato).

## Build Android (release)

Lancia `build-apk.bat`.

- Versione in `composeApp/build.gradle.kts`
- APK: `composeApp/build/outputs/apk/release/toc_sar_KMP_<version>.apk`

## Build iOS (IPA Ad Hoc / Diawi)

Su Mac, con Xcode e `supabase-config.local.json` nella root:

```bash
cp iosApp/Configuration/Signing.xcconfig.example iosApp/Configuration/Signing.xcconfig
# inserisci il Team ID Apple (10 caratteri)
./build-ios-ipa.sh ad-hoc
```

- Versione allineata ad Android (`composeApp/build.gradle.kts`)
- IPA: `toc_sar_iOS_<version>.ipa`
- Bundle ID: `it.ansmi.tocsar`
- L’UDID del telefono deve essere nel profilo Ad Hoc Apple

Apri il progetto: `iosApp/iosApp.xcodeproj`.

## Struttura

- `composeApp` — UI e logica condivisa + entry Android/iOS
- `sql/` — schema Supabase dedicato (clonato da gestSQUADRE)
- `backend_toc/` — TOC web (sala operativa + anagrafica)
- `iosApp` — Xcode (Compose UI + framework Kotlin)
