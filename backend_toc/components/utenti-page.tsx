"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import {
  ADMIN_SESSION_STORAGE_KEY,
  canManageBackendUsers,
  type AdminSessionData,
} from "@/lib/admin-auth";
import { getSupabaseBrowserClient, restoreAdminSessionFromStorage } from "@/lib/supabase-browser";
import {
  USER_SEAT_LIMIT,
  USER_SEAT_LIMIT_REACHED_MESSAGE,
  countUserSeats,
  fetchOrgSeatCodes,
  isUserSeatLimitExempt,
  normalizeSeatCode,
  userSeatLimitMessage,
  wouldExceedUserSeatLimit,
} from "@/lib/user-seats";
import styles from "./config-page.module.css";

type BackendUser = {
  id: string;
  admin_code: string;
  admin_name: string;
  is_enabled: boolean;
  password_hash?: string;
};

type AppUser = {
  id: string;
  squad_code: string;
  squad_name: string;
  is_enabled: boolean;
  password_hash: string;
};

type UnifiedRow = {
  code: string;
  name: string;
  app: AppUser | null;
  backend: BackendUser | null;
  accessApp: boolean;
  accessBackend: boolean;
};

function friendlyError(err: unknown, fallback: string): string {
  const msg = err instanceof Error ? err.message : fallback;
  const lower = msg.toLowerCase();
  if (lower.includes("duplicate") || lower.includes("unique")) {
    return "Codice già presente in questo ente.";
  }
  return msg || fallback;
}

export default function UtentiPage() {
  const [supabase, setSupabase] = useState<ReturnType<typeof getSupabaseBrowserClient>>(null);
  const [session, setSession] = useState<AdminSessionData | null>(null);
  const [authChecked, setAuthChecked] = useState(false);
  const [backendRows, setBackendRows] = useState<BackendUser[]>([]);
  const [appRows, setAppRows] = useState<AppUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);

  const [editingId, setEditingId] = useState<string | null>(null);
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [password, setPassword] = useState("");
  const [enabled, setEnabled] = useState(true);
  const [alsoApp, setAlsoApp] = useState(false);

  useEffect(() => {
    setSupabase(getSupabaseBrowserClient());
    const raw = window.localStorage.getItem(ADMIN_SESSION_STORAGE_KEY);
    if (raw) {
      const restored = restoreAdminSessionFromStorage(raw);
      if (restored) {
        setSession(restored);
      } else {
        window.localStorage.removeItem(ADMIN_SESSION_STORAGE_KEY);
      }
    }
    setAuthChecked(true);
  }, []);

  const orgId = session?.organizationId ?? null;
  const orgCode = session?.organizationCode ?? "";
  const allowed = canManageBackendUsers(session?.code);
  const limitExempt = isUserSeatLimitExempt(orgCode);

  const refresh = useCallback(async () => {
    if (!supabase || !orgId || !allowed) {
      setBackendRows([]);
      setAppRows([]);
      setLoading(false);
      return;
    }
    const [backendRes, appRes] = await Promise.all([
      supabase
        .from("toc_admins")
        .select("id, admin_code, admin_name, is_enabled, password_hash")
        .eq("organization_id", orgId)
        .order("admin_code", { ascending: true }),
      supabase
        .from("squads")
        .select("id, squad_code, squad_name, is_enabled, password_hash")
        .eq("organization_id", orgId)
        .order("squad_code", { ascending: true }),
    ]);
    if (backendRes.error) {
      setError(backendRes.error.message);
      setBackendRows([]);
    } else {
      setBackendRows((backendRes.data ?? []) as BackendUser[]);
    }
    if (appRes.error) {
      setError(appRes.error.message);
      setAppRows([]);
    } else {
      setError(null);
      setAppRows((appRes.data ?? []) as AppUser[]);
    }
    setLoading(false);
  }, [supabase, orgId, allowed]);

  useEffect(() => {
    if (!authChecked) {
      return;
    }
    if (!session) {
      window.location.replace("/");
      return;
    }
    void refresh();
  }, [authChecked, session, refresh]);

  useEffect(() => {
    if (!toast) {
      return;
    }
    const t = window.setTimeout(() => setToast(null), 3500);
    return () => window.clearTimeout(t);
  }, [toast]);

  const unified = useMemo(() => {
    const map = new Map<string, UnifiedRow>();
    for (const app of appRows) {
      const codeKey = normalizeSeatCode(app.squad_code);
      if (!codeKey) {
        continue;
      }
      map.set(codeKey, {
        code: codeKey,
        name: app.squad_name,
        app,
        backend: null,
        accessApp: app.is_enabled,
        accessBackend: false,
      });
    }
    for (const backend of backendRows) {
      const codeKey = normalizeSeatCode(backend.admin_code);
      if (!codeKey) {
        continue;
      }
      const existing = map.get(codeKey);
      if (existing) {
        existing.backend = backend;
        existing.accessBackend = backend.is_enabled;
        if (!existing.name.trim()) {
          existing.name = backend.admin_name;
        }
      } else {
        map.set(codeKey, {
          code: codeKey,
          name: backend.admin_name,
          app: null,
          backend,
          accessApp: false,
          accessBackend: backend.is_enabled,
        });
      }
    }
    return [...map.values()].sort((a, b) => a.code.localeCompare(b.code, "it"));
  }, [appRows, backendRows]);

  const seatCount = useMemo(
    () =>
      countUserSeats({
        appCodes: appRows.filter((r) => r.is_enabled).map((r) => r.squad_code),
        backendCodes: backendRows.filter((r) => r.is_enabled).map((r) => r.admin_code),
      }),
    [appRows, backendRows],
  );

  function resetForm() {
    setEditingId(null);
    setCode("");
    setName("");
    setPassword("");
    setEnabled(true);
    setAlsoApp(false);
  }

  function startEdit(row: BackendUser) {
    setEditingId(row.id);
    setCode(row.admin_code);
    setName(row.admin_name);
    setPassword("");
    setEnabled(row.is_enabled);
    setAlsoApp(appRows.some((a) => normalizeSeatCode(a.squad_code) === normalizeSeatCode(row.admin_code)));
    setError(null);
  }

  async function assertSeatOk(nextCode: string, nextEnabled: boolean) {
    if (!supabase || !orgId) {
      return;
    }
    const snap = await fetchOrgSeatCodes(supabase, orgId);
    if (snap.error) {
      throw new Error(snap.error);
    }
    if (
      wouldExceedUserSeatLimit({
        orgCode,
        appCodes: snap.appCodes,
        backendCodes: snap.backendCodes,
        nextCode,
        nextEnabled,
      })
    ) {
      throw new Error(USER_SEAT_LIMIT_REACHED_MESSAGE);
    }
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!supabase || !orgId || !allowed) {
      return;
    }
    const adminCode = code.trim().toUpperCase();
    const adminName = name.trim();
    const pwd = password.trim();
    if (!adminCode || !adminName) {
      setError("Compila codice e nome.");
      return;
    }
    if (!editingId && !pwd) {
      setError("La password serve per un accesso nuovo.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      if (!editingId || enabled) {
        await assertSeatOk(adminCode, enabled);
      }
      const role = adminCode.startsWith("ADMIN_") ? "admin" : "viewer";
      if (editingId) {
        const patch: Record<string, string | boolean> = {
          admin_code: adminCode,
          admin_name: adminName,
          is_enabled: enabled,
          role,
        };
        if (pwd) {
          patch.password_hash = pwd;
        }
        const { error: updateError } = await supabase
          .from("toc_admins")
          .update(patch)
          .eq("id", editingId)
          .eq("organization_id", orgId);
        if (updateError) {
          throw updateError;
        }
        setToast("Accesso backend aggiornato.");
      } else {
        const { error: insertError } = await supabase.from("toc_admins").insert({
          admin_code: adminCode,
          admin_name: adminName,
          password_hash: pwd,
          role,
          is_enabled: enabled,
          organization_id: orgId,
        });
        if (insertError) {
          throw insertError;
        }
        setToast("Accesso backend inserito.");
      }

      const appMatch = appRows.find((a) => normalizeSeatCode(a.squad_code) === adminCode);
      if (alsoApp) {
        if (appMatch) {
          if (!appMatch.is_enabled) {
            await assertSeatOk(adminCode, true);
            const { error: enErr } = await supabase
              .from("squads")
              .update({ is_enabled: true, squad_name: adminName })
              .eq("id", appMatch.id)
              .eq("organization_id", orgId);
            if (enErr) {
              throw enErr;
            }
          }
        } else {
          await assertSeatOk(adminCode, enabled);
          const appPwd = pwd || backendRows.find((b) => b.id === editingId)?.password_hash || "";
          if (!appPwd) {
            throw new Error("Per abilitare anche l'app serve una password.");
          }
          const { error: appIns } = await supabase.from("squads").insert({
            squad_code: adminCode,
            squad_name: adminName,
            password_hash: appPwd,
            map_color: "#e0be3a",
            map_icon_key: "circle",
            is_enabled: enabled,
            organization_id: orgId,
          });
          if (appIns) {
            throw appIns;
          }
        }
      } else if (appMatch?.is_enabled) {
        const { error: disErr } = await supabase
          .from("squads")
          .update({ is_enabled: false })
          .eq("id", appMatch.id)
          .eq("organization_id", orgId);
        if (disErr) {
          throw disErr;
        }
      }

      resetForm();
      await refresh();
    } catch (err) {
      setError(friendlyError(err, "Salvataggio non riuscito."));
    } finally {
      setBusy(false);
    }
  }

  async function setAccessFlags(row: UnifiedRow, nextApp: boolean, nextBackend: boolean) {
    if (!supabase || !orgId || !allowed) {
      return;
    }
    if (!nextApp && !nextBackend) {
      setError("Lascia almeno un accesso (app o backend), oppure usa Rimuovi.");
      return;
    }
    const selfBackend =
      row.backend &&
      normalizeSeatCode(row.backend.admin_code) === normalizeSeatCode(session?.code);
    if (selfBackend && !nextBackend) {
      setError("Non puoi togliere l'accesso backend con cui sei entrato.");
      return;
    }

    setBusy(true);
    setError(null);
    try {
      if ((nextApp || nextBackend) && !(row.accessApp || row.accessBackend)) {
        await assertSeatOk(row.code, true);
      }

      if (nextBackend) {
        if (row.backend) {
          if (!row.backend.is_enabled) {
            const { error: up } = await supabase
              .from("toc_admins")
              .update({ is_enabled: true, admin_name: row.name })
              .eq("id", row.backend.id)
              .eq("organization_id", orgId);
            if (up) {
              throw up;
            }
          }
        } else {
          const pwd =
            row.app?.password_hash?.trim() ||
            window.prompt(`Password TOC per ${row.code} (accesso backend):`)?.trim() ||
            "";
          if (!pwd) {
            throw new Error("Password obbligatoria per creare l'accesso backend.");
          }
          const role = row.code.startsWith("ADMIN_") ? "admin" : "viewer";
          const { error: ins } = await supabase.from("toc_admins").insert({
            admin_code: row.code,
            admin_name: row.name || row.code,
            password_hash: pwd,
            role,
            is_enabled: true,
            organization_id: orgId,
          });
          if (ins) {
            throw ins;
          }
        }
      } else if (row.backend) {
        const { error: up } = await supabase
          .from("toc_admins")
          .update({ is_enabled: false })
          .eq("id", row.backend.id)
          .eq("organization_id", orgId);
        if (up) {
          throw up;
        }
      }

      if (nextApp) {
        if (row.app) {
          if (!row.app.is_enabled) {
            const { error: up } = await supabase
              .from("squads")
              .update({ is_enabled: true, squad_name: row.name })
              .eq("id", row.app.id)
              .eq("organization_id", orgId);
            if (up) {
              throw up;
            }
          }
        } else {
          const pwd =
            row.backend?.password_hash?.trim() ||
            window.prompt(`Password app per ${row.code}:`)?.trim() ||
            "";
          if (!pwd) {
            throw new Error("Password obbligatoria per creare l'accesso app.");
          }
          const { error: ins } = await supabase.from("squads").insert({
            squad_code: row.code,
            squad_name: row.name || row.code,
            password_hash: pwd,
            map_color: "#e0be3a",
            map_icon_key: "circle",
            is_enabled: true,
            organization_id: orgId,
          });
          if (ins) {
            throw ins;
          }
        }
      } else if (row.app) {
        const { error: up } = await supabase
          .from("squads")
          .update({ is_enabled: false })
          .eq("id", row.app.id)
          .eq("organization_id", orgId);
        if (up) {
          throw up;
        }
      }

      setToast(`Accessi aggiornati per ${row.code}.`);
      await refresh();
    } catch (err) {
      setError(friendlyError(err, "Aggiornamento accessi non riuscito."));
    } finally {
      setBusy(false);
    }
  }

  async function removeUser(row: BackendUser) {
    if (!supabase || !orgId || !allowed) {
      return;
    }
    if (row.admin_code.toUpperCase() === session?.code.trim().toUpperCase()) {
      setError("Non puoi rimuovere l'accesso con cui sei entrato.");
      return;
    }
    const ok = window.confirm(
      `Rimuovere l'accesso backend ${row.admin_code} — ${row.admin_name}? Non potrà più entrare nel TOC.`,
    );
    if (!ok) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const { error: deleteError } = await supabase
        .from("toc_admins")
        .delete()
        .eq("id", row.id)
        .eq("organization_id", orgId);
      if (deleteError) {
        throw deleteError;
      }
      if (editingId === row.id) {
        resetForm();
      }
      setToast(`${row.admin_code} rimosso dal backend.`);
      await refresh();
    } catch (err) {
      setError(friendlyError(err, "Rimozione non riuscita."));
    } finally {
      setBusy(false);
    }
  }

  if (!authChecked || !session) {
    return <div className={styles.root}>Caricamento…</div>;
  }

  if (!allowed) {
    return (
      <div className={styles.root}>
        <header className={styles.topBar}>
          <div>
            <h1>Utenti backend — {session.organizationCode}</h1>
            <p className={styles.sub}>Solo un codice che inizia con ADMIN_ può gestire gli accessi al backend.</p>
          </div>
          <div className={styles.nav}>
            <Link className={styles.backLink} href="/">
              ← Sala operativa
            </Link>
          </div>
        </header>
      </div>
    );
  }

  return (
    <div className={styles.root}>
      <header className={styles.topBar}>
        <div>
          <h1>Utenti — {session.organizationCode}</h1>
          <p className={styles.sub}>
            Backend e app nello stesso elenco. Stesso codice = 1 posto.
            {limitExempt
              ? " Nessun limite per NVANSMI."
              : ` Posti ${seatCount} / ${USER_SEAT_LIMIT}.`}
          </p>
        </div>
        <div className={styles.nav}>
          <Link className={styles.backLink} href="/">
            ← Sala operativa
          </Link>
        </div>
      </header>
      <section className={styles.panel}>
        <p className={styles.hint}>{userSeatLimitMessage(orgCode)}</p>
        <form className={styles.form} onSubmit={(e) => void handleSubmit(e)}>
          <div className={styles.formRow}>
            <label className={styles.field}>
              Codice
              <input
                value={code}
                onChange={(e) => setCode(e.target.value.toUpperCase())}
                disabled={busy}
                placeholder="ADMIN_… o codice operatore"
                autoComplete="off"
              />
            </label>
            <label className={styles.field}>
              Nome
              <input
                value={name}
                onChange={(e) => setName(e.target.value)}
                disabled={busy}
                autoComplete="name"
              />
            </label>
            <label className={styles.field}>
              Password
              <input
                type="text"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                disabled={busy}
                placeholder={editingId ? "vuota = non cambia" : "obbligatoria"}
                autoComplete="new-password"
              />
            </label>
            <label className={styles.checkField}>
              <input
                type="checkbox"
                checked={enabled}
                disabled={busy}
                onChange={(e) => setEnabled(e.target.checked)}
              />
              Backend attivo
            </label>
            <label className={styles.checkField}>
              <input
                type="checkbox"
                checked={alsoApp}
                disabled={busy}
                onChange={(e) => setAlsoApp(e.target.checked)}
              />
              Anche accesso app
            </label>
          </div>
          <div className={styles.formActions}>
            <button className={styles.primaryBtn} type="submit" disabled={busy}>
              {busy ? "…" : editingId ? "Salva modifiche" : "Inserisci accesso"}
            </button>
            {editingId ? (
              <button className={styles.secondaryBtn} type="button" disabled={busy} onClick={resetForm}>
                Annulla
              </button>
            ) : null}
          </div>
        </form>
        {toast ? <p className={styles.toast}>{toast}</p> : null}
        {error ? <p className={styles.error}>{error}</p> : null}
        <div className={styles.list}>
          {loading && unified.length === 0 ? (
            <p className={styles.empty}>Caricamento…</p>
          ) : unified.length === 0 ? (
            <p className={styles.empty}>Nessun utente in questo ente.</p>
          ) : (
            unified.map((row) => {
              const self =
                row.backend &&
                normalizeSeatCode(row.backend.admin_code) === normalizeSeatCode(session.code);
              return (
                <article key={row.code} className={styles.card}>
                  <div className={styles.cardHead}>
                    <h2 className={styles.title}>
                      {row.code} — {row.name}
                      {self ? " (tu)" : ""}
                    </h2>
                  </div>
                  <div className={styles.flagRow}>
                    <input
                      id={`app-${row.code}`}
                      type="checkbox"
                      checked={row.accessApp}
                      disabled={busy}
                      onChange={(e) => void setAccessFlags(row, e.target.checked, row.accessBackend)}
                    />
                    <label className={styles.flagLabel} htmlFor={`app-${row.code}`}>
                      Accesso app
                    </label>
                  </div>
                  <div className={styles.flagRow}>
                    <input
                      id={`be-${row.code}`}
                      type="checkbox"
                      checked={row.accessBackend}
                      disabled={busy || Boolean(self && row.accessBackend)}
                      onChange={(e) => void setAccessFlags(row, row.accessApp, e.target.checked)}
                    />
                    <label className={styles.flagLabel} htmlFor={`be-${row.code}`}>
                      Accesso backend
                      {row.code.startsWith("ADMIN_") ? " · può gestire gli accessi" : ""}
                    </label>
                  </div>
                  <p className={row.accessApp || row.accessBackend ? styles.statusOn : styles.statusOff}>
                    {row.accessApp && row.accessBackend
                      ? "App + backend (1 posto)"
                      : row.accessApp
                        ? "Solo app"
                        : row.accessBackend
                          ? "Solo backend"
                          : "Disattivo"}
                  </p>
                  <div className={styles.cardActions}>
                    {row.backend ? (
                      <>
                        <button
                          type="button"
                          className={styles.secondaryBtn}
                          disabled={busy}
                          onClick={() => startEdit(row.backend!)}
                        >
                          Modifica backend
                        </button>
                        <button
                          type="button"
                          className={styles.toggleBtn}
                          disabled={busy || Boolean(self)}
                          onClick={() => void removeUser(row.backend!)}
                        >
                          Rimuovi backend
                        </button>
                      </>
                    ) : (
                      <p className={styles.meta}>Solo in anagrafica app — spunta Backend per creare l&apos;accesso TOC.</p>
                    )}
                  </div>
                </article>
              );
            })
          )}
        </div>
      </section>
    </div>
  );
}
