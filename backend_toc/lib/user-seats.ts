/** Posti utenti per ente: stessi codice app+backend contano 1. NVANSMI senza limite. */

export const USER_SEAT_LIMIT = 10;

export function isUserSeatLimitExempt(orgCode: string | null | undefined): boolean {
  return (orgCode ?? "").trim().toUpperCase() === "NVANSMI";
}

export function normalizeSeatCode(code: string | null | undefined): string {
  return (code ?? "").trim().toUpperCase();
}

/** Codici attivi (app e/o backend): unione → 1 posto per codice. */
export function collectActiveSeatCodes(input: {
  appCodes: Array<string | null | undefined>;
  backendCodes: Array<string | null | undefined>;
}): Set<string> {
  const seats = new Set<string>();
  for (const c of input.appCodes) {
    const code = normalizeSeatCode(c);
    if (code) {
      seats.add(code);
    }
  }
  for (const c of input.backendCodes) {
    const code = normalizeSeatCode(c);
    if (code) {
      seats.add(code);
    }
  }
  return seats;
}

export function countUserSeats(input: {
  appCodes: Array<string | null | undefined>;
  backendCodes: Array<string | null | undefined>;
}): number {
  return collectActiveSeatCodes(input).size;
}

/**
 * true se aggiungere/attivare `code` supera il limite.
 * Se il codice è già tra i posti attivi, non consuma un posto in più.
 */
export function wouldExceedUserSeatLimit(input: {
  orgCode: string | null | undefined;
  appCodes: Array<string | null | undefined>;
  backendCodes: Array<string | null | undefined>;
  nextCode: string;
  nextEnabled?: boolean;
}): boolean {
  if (isUserSeatLimitExempt(input.orgCode)) {
    return false;
  }
  if (input.nextEnabled === false) {
    return false;
  }
  const code = normalizeSeatCode(input.nextCode);
  if (!code) {
    return false;
  }
  const seats = collectActiveSeatCodes(input);
  if (seats.has(code)) {
    return false;
  }
  return seats.size >= USER_SEAT_LIMIT;
}

export function userSeatLimitMessage(orgCode: string | null | undefined): string {
  if (isUserSeatLimitExempt(orgCode)) {
    return "Nessun limite posti utenti per questo ente.";
  }
  return `Limite ${USER_SEAT_LIMIT} utenti (stesso codice app+backend = 1 posto).`;
}

export const USER_SEAT_LIMIT_REACHED_MESSAGE =
  `Limite raggiunto: massimo ${USER_SEAT_LIMIT} utenti per questo ente. Inviare mail a info@rolexpress-service.eu per l'acquisto di un nuovo pacchetto utenti.`;

// Client Supabase browser (thenable query builder).
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export async function fetchOrgSeatCodes(
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  supabase: any,
  orgId: string,
): Promise<{ appCodes: string[]; backendCodes: string[]; error: string | null }> {
  const [squadRes, adminRes] = await Promise.all([
    supabase.from("squads").select("squad_code, is_enabled").eq("organization_id", orgId),
    supabase.from("toc_admins").select("admin_code, is_enabled").eq("organization_id", orgId),
  ]);
  if (squadRes.error) {
    return { appCodes: [], backendCodes: [], error: String(squadRes.error.message) };
  }
  if (adminRes.error) {
    return { appCodes: [], backendCodes: [], error: String(adminRes.error.message) };
  }
  const appCodes = ((squadRes.data ?? []) as Array<{ squad_code?: string; is_enabled?: boolean }>)
    .filter((r) => r.is_enabled)
    .map((r) => String(r.squad_code ?? ""));
  const backendCodes = ((adminRes.data ?? []) as Array<{ admin_code?: string; is_enabled?: boolean }>)
    .filter((r) => r.is_enabled)
    .map((r) => String(r.admin_code ?? ""));
  return { appCodes, backendCodes, error: null };
}
