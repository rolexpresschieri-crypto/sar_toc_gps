"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import {
  ADMIN_SESSION_STORAGE_KEY,
  canManageBackendUsers,
  type AdminSessionData,
} from "@/lib/admin-auth";
import {
  forceLogoutOperator,
  hasGpsFix,
  loadOnlineOperators,
  setPeerVisible,
  type OnlineOperatorRow,
} from "@/lib/session-control";
import { getSupabaseBrowserClient, restoreAdminSessionFromStorage } from "@/lib/supabase-browser";
import styles from "./config-page.module.css";

const POLL_MS = 3000;

function displaySurnameFirst(raw: string): string {
  const name = raw.trim().replace(/\s+/g, " ");
  const parts = name.split(" ");
  if (parts.length <= 1) {
    return name;
  }
  return `${parts[parts.length - 1]} ${parts.slice(0, -1).join(" ")}`;
}

export default function ConfigPage() {
  const [supabase, setSupabase] = useState<ReturnType<typeof getSupabaseBrowserClient>>(null);
  const [session, setSession] = useState<AdminSessionData | null>(null);
  const [authChecked, setAuthChecked] = useState(false);
  const [rows, setRows] = useState<OnlineOperatorRow[]>([]);
  const [status, setStatus] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [confirmTarget, setConfirmTarget] = useState<OnlineOperatorRow | null>(null);

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

  const refresh = useCallback(async () => {
    if (!supabase || !orgId) {
      setRows([]);
      setLoading(false);
      return;
    }
    const result = await loadOnlineOperators(supabase, orgId);
    setRows(result.rows);
    setStatus(result.error);
    setLoading(false);
  }, [supabase, orgId]);

  useEffect(() => {
    if (!authChecked) {
      return;
    }
    if (!session) {
      window.location.replace("/");
      return;
    }
    void refresh();
    const t = window.setInterval(() => void refresh(), POLL_MS);
    return () => window.clearInterval(t);
  }, [authChecked, session, refresh]);

  useEffect(() => {
    if (!toast) {
      return;
    }
    const t = window.setTimeout(() => setToast(null), 3500);
    return () => window.clearTimeout(t);
  }, [toast]);

  async function setVisible(row: OnlineOperatorRow, visible: boolean) {
    if (!supabase || !orgId) {
      return;
    }
    setBusyId(row.sessionId);
    try {
      const error = await setPeerVisible(supabase, row.sessionId, orgId, visible);
      if (error) {
        setStatus(error);
        return;
      }
      setToast(
        visible
          ? `${row.squadCode}: visibile agli altri operatori`
          : `${row.squadCode}: nascosto, lo vede solo il TOC`,
      );
      await refresh();
    } finally {
      setBusyId(null);
    }
  }

  async function confirmForceLogout() {
    if (!supabase || !orgId || !confirmTarget) {
      return;
    }
    const target = confirmTarget;
    setBusyId(target.sessionId);
    try {
      const error = await forceLogoutOperator(supabase, target, orgId);
      if (error) {
        setStatus(error);
        return;
      }
      setToast(`Log-out forzato: ${target.squadCode}`);
      setConfirmTarget(null);
      await refresh();
    } finally {
      setBusyId(null);
    }
  }

  if (!authChecked || !session) {
    return <div className={styles.root}>Caricamento…</div>;
  }

  return (
    <div className={styles.root}>
      <header className={styles.topBar}>
        <div>
          <h1>Operatori online — {session.organizationCode}</h1>
          <p className={styles.sub}>Come in app: visibilità in mappa e log-out forzato. Ogni utente di questo ente può farlo.</p>
        </div>
        <div className={styles.nav}>
          {canManageBackendUsers(session.code) ? (
            <Link className={styles.backLink} href="/utenti">
              Utenti backend
            </Link>
          ) : null}
          <Link className={styles.backLink} href="/">
            ← Sala operativa
          </Link>
        </div>
      </header>
      <section className={styles.panel}>
        <p className={styles.hint}>
          Flag acceso: l&apos;operatore compare sulla mappa degli altri. Spento: lo vede solo il TOC
          (e LUPO in app). Forza log-out lo toglie dalla mappa finché non fa di nuovo Log-in.
        </p>
        {toast ? <p className={styles.toast}>{toast}</p> : null}
        {status ? <p className={styles.error}>{status}</p> : null}
        <div className={styles.list}>
          {loading && rows.length === 0 ? (
            <p className={styles.empty}>Caricamento…</p>
          ) : rows.length === 0 ? (
            <p className={styles.empty}>Nessun operatore online.</p>
          ) : (
            rows.map((row) => {
              const gps = hasGpsFix(row);
              const busy = busyId === row.sessionId;
              return (
                <article key={row.sessionId} className={styles.card}>
                  <div className={styles.cardHead}>
                    <span className={styles.badge} style={{ background: row.mapColor }} />
                    <h2 className={styles.title}>
                      {row.squadCode} — {displaySurnameFirst(row.squadName)}
                    </h2>
                  </div>
                  <p className={gps ? styles.gpsOk : styles.gpsMissing}>
                    {gps ? "GPS: posizione nota" : "GPS: nessun fix ancora"}
                  </p>
                  {row.loginAt ? (
                    <p className={styles.meta}>Login: {new Date(row.loginAt).toLocaleString("it-IT")}</p>
                  ) : null}
                  <label className={styles.flagRow}>
                    <input
                      type="checkbox"
                      checked={row.peerVisible}
                      disabled={busy}
                      onChange={(e) => void setVisible(row, e.target.checked)}
                    />
                    <span className={styles.flagLabel}>
                      {row.peerVisible
                        ? "Visibile in mappa (agli altri operatori)"
                        : "Nascosto: solo il TOC lo vede in mappa"}
                    </span>
                  </label>
                  <button
                    type="button"
                    className={styles.logoutBtn}
                    disabled={busy}
                    onClick={() => setConfirmTarget(row)}
                  >
                    {busy ? "…" : "Forza log-out"}
                  </button>
                </article>
              );
            })
          )}
        </div>
      </section>
      {confirmTarget ? (
        <div className={styles.modal} role="dialog" aria-modal="true">
          <div className={styles.modalCard}>
            <h2>Forza log-out</h2>
            <p>
              Disconnettere {confirmTarget.squadCode} ({displaySurnameFirst(confirmTarget.squadName)})?
              Sparisce dalla mappa finché non fa di nuovo Log-in.
            </p>
            <div className={styles.modalActions}>
              <button
                type="button"
                className={styles.modalCancel}
                disabled={busyId === confirmTarget.sessionId}
                onClick={() => setConfirmTarget(null)}
              >
                Annulla
              </button>
              <button
                type="button"
                className={styles.modalConfirm}
                disabled={busyId === confirmTarget.sessionId}
                onClick={() => void confirmForceLogout()}
              >
                Forza log-out
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}
