"use client";

import { useEffect, useState, type FormEvent } from "react";
import {
  ADMIN_ENTE_STORAGE_KEY,
  ADMIN_SESSION_STORAGE_KEY,
  normalizeOrgCode,
  type AdminSessionData,
} from "@/lib/admin-auth";
import {
  getSupabaseBrowserClient,
  loginTocAdmin,
  restoreAdminSessionFromStorage,
} from "@/lib/supabase-browser";
import styles from "@/components/anagrafica.module.css";

export function useTocSession() {
  const [supabase, setSupabase] = useState<ReturnType<typeof getSupabaseBrowserClient>>(null);
  const [session, setSession] = useState<AdminSessionData | null>(null);
  const [authChecked, setAuthChecked] = useState(false);
  const [loginEnte, setLoginEnte] = useState("NVANSMI");
  const [loginCode, setLoginCode] = useState("ADMIN_RR");
  const [loginPassword, setLoginPassword] = useState("");
  const [enteLocked, setEnteLocked] = useState(false);
  const [loginBusy, setLoginBusy] = useState(false);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);

  useEffect(() => {
    setSupabase(getSupabaseBrowserClient());
    const savedEnte = window.localStorage.getItem(ADMIN_ENTE_STORAGE_KEY)?.trim();
    if (savedEnte) {
      setLoginEnte(normalizeOrgCode(savedEnte));
      setEnteLocked(true);
    }
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

  function persistSession(next: AdminSessionData) {
    window.localStorage.setItem(ADMIN_SESSION_STORAGE_KEY, JSON.stringify(next));
    window.localStorage.setItem(ADMIN_ENTE_STORAGE_KEY, next.organizationCode);
    setSession(next);
  }

  async function handleLogin(e: FormEvent) {
    e.preventDefault();
    if (!supabase) {
      setStatusMessage("Manca NEXT_PUBLIC_SUPABASE_URL / ANON_KEY.");
      return;
    }
    setLoginBusy(true);
    setStatusMessage(null);
    try {
      const { session: next, error } = await loginTocAdmin(
        supabase,
        loginEnte,
        loginCode,
        loginPassword,
      );
      if (error || !next) {
        setStatusMessage(error ?? "Login non riuscito.");
        return;
      }
      persistSession(next);
      setEnteLocked(true);
    } finally {
      setLoginBusy(false);
    }
  }

  function handleLogout() {
    window.localStorage.removeItem(ADMIN_SESSION_STORAGE_KEY);
    setSession(null);
    setLoginPassword("");
  }

  function handleChangeEnte() {
    setEnteLocked(false);
    setLoginEnte("");
    window.localStorage.removeItem(ADMIN_ENTE_STORAGE_KEY);
  }

  return {
    supabase,
    session,
    authChecked,
    loginEnte,
    setLoginEnte,
    loginCode,
    setLoginCode,
    loginPassword,
    setLoginPassword,
    enteLocked,
    loginBusy,
    statusMessage,
    handleLogin,
    handleLogout,
    handleChangeEnte,
  };
}

export function TocLoginForm({
  loginEnte,
  setLoginEnte,
  loginCode,
  setLoginCode,
  loginPassword,
  setLoginPassword,
  enteLocked,
  loginBusy,
  statusMessage,
  onLogin,
  onChangeEnte,
}: {
  loginEnte: string;
  setLoginEnte: (v: string) => void;
  loginCode: string;
  setLoginCode: (v: string) => void;
  loginPassword: string;
  setLoginPassword: (v: string) => void;
  enteLocked: boolean;
  loginBusy: boolean;
  statusMessage: string | null;
  onLogin: (e: FormEvent) => void;
  onChangeEnte: () => void;
}) {
  return (
    <main className={`${styles.root} ${styles.loginScreen}`}>
      <div className={styles.loginLayout}>
        <div className={styles.loginLogoWrap}>
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            className={styles.loginLogoSide}
            src="/map/squad/logo_ansmi.png"
            alt="NV ANSMI — Reparto cinofilo da soccorso"
          />
        </div>
        <form className={styles.loginCard} onSubmit={onLogin}>
          <h1>Login TOC SAR</h1>
          <input
            placeholder="Ente"
            value={loginEnte}
            onChange={(e) => setLoginEnte(e.target.value.toUpperCase())}
            disabled={enteLocked}
            autoComplete="organization"
          />
          {enteLocked ? (
            <button type="button" className={styles.changeEnte} onClick={onChangeEnte}>
              Cambia ente
            </button>
          ) : null}
          <input
            placeholder="Codice admin"
            value={loginCode}
            onChange={(e) => setLoginCode(e.target.value.toUpperCase())}
            autoComplete="username"
          />
          <input
            type="password"
            placeholder="Password"
            value={loginPassword}
            onChange={(e) => setLoginPassword(e.target.value)}
            autoComplete="current-password"
          />
          <button className={styles.loginBtn} type="submit" disabled={loginBusy}>
            {loginBusy ? "Accesso…" : "Accedi"}
          </button>
          <p className={styles.loginHint}>Come l&apos;app: ente + codice + password</p>
          {statusMessage ? <p className={styles.message}>{statusMessage}</p> : null}
        </form>
      </div>
      <p className={styles.loginSignature}>by R. Ronco</p>
    </main>
  );
}
