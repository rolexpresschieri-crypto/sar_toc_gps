import { normalizeOrgCode } from "@/lib/admin-auth";

/** File in `public/loghi/{ORG_CODE}.png` (copia da cartella progetto `loghi/`). */
export function enteLogoUrl(orgCode: string | null | undefined): string | null {
  const code = normalizeOrgCode(orgCode ?? "");
  if (!code) {
    return null;
  }
  return `/loghi/${encodeURIComponent(code)}.png`;
}
