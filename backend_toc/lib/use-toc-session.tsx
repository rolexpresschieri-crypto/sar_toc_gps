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
  const [showPassword, setShowPassword] = useState(false);
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
            placeholder="Codice TOC"
            value={loginEnte}
            onChange={(e) => setLoginEnte(e.target.value.toUpperCase())}
            disabled={enteLocked}
            autoComplete="organization"
          />
          {enteLocked ? (
            <button type="button" className={styles.changeEnte} onClick={onChangeEnte}>
              Cambia TOC
            </button>
          ) : null}
          <input
            placeholder="Codice admin"
            value={loginCode}
            onChange={(e) => setLoginCode(e.target.value.toUpperCase())}
            autoComplete="username"
          />
          <label className={styles.loginField}>
            Password
            <div className={styles.passwordWrap}>
              <input
                type={showPassword ? "text" : "password"}
                placeholder="Password"
                value={loginPassword}
                onChange={(e) => setLoginPassword(e.target.value)}
                autoComplete="current-password"
              />
              <button
                type="button"
                className={styles.passwordEye}
                onClick={() => setShowPassword((v) => !v)}
                aria-label={showPassword ? "Nascondi password" : "Mostra password"}
              >
                <PasswordEyeIcon slashed={!showPassword} />
              </button>
            </div>
          </label>
          <button className={styles.loginBtn} type="submit" disabled={loginBusy}>
            {loginBusy ? "Accesso…" : "Accedi"}
          </button>
          {statusMessage ? <p className={styles.message}>{statusMessage}</p> : null}
        </form>
      </div>
      <p className={styles.loginSignature}>by R. Ronco</p>
    </main>
  );
}

function PasswordEyeIcon({ slashed }: { slashed: boolean }) {
  return (
    <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
      <path
        d="M2.2 12S5.8 6.2 12 6.2 21.8 12 21.8 12 18.2 17.8 12 17.8 2.2 12 2.2 12z"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
      />
      <circle cx="12" cy="12" r="2.4" fill="currentColor" />
      {slashed ? (
        <path d="M5 18 L19 6" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
      ) : null}
    </svg>
  );
}
