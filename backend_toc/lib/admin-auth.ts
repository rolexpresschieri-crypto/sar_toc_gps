export const ADMIN_SESSION_STORAGE_KEY = "toc_sar_toc_session";
export const ADMIN_ENTE_STORAGE_KEY = "toc_sar_toc_ente";

export type AdminRole = "admin" | "viewer";

export type AdminSessionData = {
  code: string;
  name: string;
  role: AdminRole;
  adminId?: string;
  organizationId: string;
  organizationCode: string;
  organizationName: string;
};

export function normalizeAdminRole(value: string | null | undefined): AdminRole {
  const v = typeof value === "string" ? value.trim().toLowerCase() : "";
  if (v === "viewer") {
    return "viewer";
  }
  return "admin";
}

/** Qualsiasi accesso TOC abilitato gestisce anagrafica / sala (operatori, missioni, log). */
export function canManageAnagrafica(session: AdminSessionData | null): boolean {
  return Boolean(session);
}

/** Solo un codice che inizia con ADMIN_ gestisce Utenti backend (inserire/modificare accessi TOC). */
export function canManageBackendUsers(code: string | null | undefined): boolean {
  return (code ?? "").trim().toUpperCase().startsWith("ADMIN_");
}

export function normalizeOrgCode(value: string): string {
  return value.trim().toUpperCase();
}
