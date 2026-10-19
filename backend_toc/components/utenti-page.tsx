"use client";

import Link from "next/link";
import { useCallback, useEffect, useState, type FormEvent } from "react";
import {
  ADMIN_SESSION_STORAGE_KEY,
  canManageBackendUsers,
  type AdminSessionData,
} from "@/lib/admin-auth";
import { getSupabaseBrowserClient, restoreAdminSessionFromStorage } from "@/lib/supabase-browser";
import styles from "./config-page.module.css";

type BackendUser = {
  id: string;
  admin_code: string;
  admin_name: string;
  is_enabled: boolean;
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
  const [rows, setRows] = useState<BackendUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);

  const [editingId, setEditingId] = useState<string | null>(null);
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [password, setPassword] = useState("");
  const [enabled, setEnabled] = useState(true);

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
  const allowed = canManageBackendUsers(session?.code);

  const refresh = useCallback(async () => {
    if (!supabase || !orgId || !allowed) {
      setRows([]);
      setLoading(false);
      return;
    }
    const { data, error: loadError } = await supabase
      .from("toc_admins")
      .select("id, admin_code, admin_name, is_enabled")
      .eq("organization_id", orgId)
      .order("admin_code", { ascending: true });
    if (loadError) {
      setError(loadError.message);
      setRows([]);
    } else {
      setError(null);
      setRows((data ?? []) as BackendUser[]);
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

  function resetForm() {
    setEditingId(null);
    setCode("");
    setName("");
    setPassword("");
    setEnabled(true);
  }

  function startEdit(row: BackendUser) {
    setEditingId(row.id);
    setCode(row.admin_code);
    setName(row.admin_name);
    setPassword("");
    setEnabled(row.is_enabled);
    setError(null);
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
      resetForm();
      await refresh();
    } catch (err) {
      setError(friendlyError(err, "Salvataggio non riuscito."));
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
      setToast(`${row.admin_code} rimosso.`);
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
          <h1>Utenti backend — {session.organizationCode}</h1>
          <p className={styles.sub}>
            Accessi al TOC di questo ente. Gli operatori dell&apos;app restano in Anagrafica.
          </p>
        </div>
        <div className={styles.nav}>
          <Link className={styles.backLink} href="/">
            ← Sala operativa
          </Link>
        </div>
      </header>
      <section className={styles.panel}>
        <form className={styles.form} onSubmit={(e) => void handleSubmit(e)}>
          <div className={styles.formRow}>
            <label className={styles.field}>
              Codice
              <input
                value={code}
                onChange={(e) => setCode(e.target.value.toUpperCase())}
                disabled={busy}
                placeholder="ADMIN_…"
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
              Attivo
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
          {loading && rows.length === 0 ? (
            <p className={styles.empty}>Caricamento…</p>
          ) : rows.length === 0 ? (
            <p className={styles.empty}>Nessun accesso backend in questo ente.</p>
          ) : (
            rows.map((row) => {
              const self = row.admin_code.toUpperCase() === session.code.trim().toUpperCase();
              return (
                <article key={row.id} className={styles.card}>
                  <div className={styles.cardHead}>
                    <h2 className={styles.title}>
                      {row.admin_code} — {row.admin_name}
                      {self ? " (tu)" : ""}
                    </h2>
                  </div>
                  <p className={row.is_enabled ? styles.statusOn : styles.statusOff}>
                    {row.is_enabled ? "Attivo" : "Non attivo"}
                    {row.admin_code.toUpperCase().startsWith("ADMIN_")
                      ? " · può gestire gli accessi"
                      : " · solo ingresso al TOC"}
                  </p>
                  <div className={styles.cardActions}>
                    <button
                      type="button"
                      className={styles.secondaryBtn}
                      disabled={busy}
                      onClick={() => startEdit(row)}
                    >
                      Modifica
                    </button>
                    <button
                      type="button"
                      className={styles.toggleBtn}
                      disabled={busy || self}
                      onClick={() => void removeUser(row)}
                    >
                      Rimuovi
                    </button>
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
