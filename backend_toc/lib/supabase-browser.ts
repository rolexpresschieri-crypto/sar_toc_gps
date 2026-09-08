import { createClient, type SupabaseClient } from "@supabase/supabase-js";
import {
  normalizeAdminRole,
  normalizeOrgCode,
  type AdminSessionData,
} from "@/lib/admin-auth";

let client: SupabaseClient | null = null;

export function getSupabaseBrowserClient(): SupabaseClient | null {
  if (client) {
    return client;
  }
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL?.trim();
  const key = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY?.trim();
  if (!url || !key) {
    return null;
  }
  client = createClient(url, key);
  return client;
}

type AdminLoginRow = {
  id: string;
  admin_code: string;
  admin_name: string;
  password_hash: string;
  role: string;
  organization_id?: string | null;
};

export async function loginTocAdmin(
  supabase: SupabaseClient,
  orgCodeInput: string,
  loginCode: string,
  loginPassword: string,
): Promise<{ session?: AdminSessionData; error?: string }> {
  const orgCode = normalizeOrgCode(orgCodeInput);
  if (!orgCode) {
    return { error: "Inserisci il codice ente." };
  }

  const { data: org, error: orgError } = await supabase
    .from("organizations")
    .select("id, org_code, org_name, is_enabled")
    .eq("org_code", orgCode)
    .maybeSingle();

  if (orgError) {
    return { error: orgError.message };
  }
  if (!org) {
    return { error: "Ente non in anagrafica." };
  }
  if (org.is_enabled === false) {
    return { error: "Ente sospeso. Contatta chi gestisce gli enti." };
  }

  const adminCode = loginCode.trim().toUpperCase();
  const baseSelect = "id, admin_code, admin_name, password_hash, role, is_enabled";

  let data: AdminLoginRow | null = null;
  const scoped = await supabase
    .from("toc_admins")
    .select(`${baseSelect}, organization_id`)
    .eq("admin_code", adminCode)
    .eq("is_enabled", true)
    .maybeSingle();

  if (scoped.error && scoped.error.message.toLowerCase().includes("organization_id")) {
    const legacy = await supabase
      .from("toc_admins")
      .select(baseSelect)
      .eq("admin_code", adminCode)
      .eq("is_enabled", true)
      .maybeSingle();
    if (legacy.error) {
      return { error: legacy.error.message };
    }
    data = (legacy.data as AdminLoginRow | null) ?? null;
  } else if (scoped.error) {
    return { error: scoped.error.message };
  } else {
    data = (scoped.data as AdminLoginRow | null) ?? null;
    if (data?.organization_id && data.organization_id !== org.id) {
      return { error: "Credenziali non valide per questo ente." };
    }
  }

  if (!data) {
    return { error: "Credenziali non valide." };
  }
  if (data.password_hash !== loginPassword.trim()) {
    return { error: "Password errata." };
  }

  return {
    session: {
      code: data.admin_code,
      name: data.admin_name,
      role: normalizeAdminRole(data.role),
      adminId: data.id,
      organizationId: org.id as string,
      organizationCode: org.org_code as string,
      organizationName: org.org_name as string,
    },
  };
}

export function restoreAdminSessionFromStorage(raw: string): AdminSessionData | null {
  try {
    const parsed = JSON.parse(raw) as AdminSessionData;
    if (
      !parsed.code?.trim() ||
      !parsed.name?.trim() ||
      !parsed.organizationId?.trim() ||
      !parsed.organizationCode?.trim()
    ) {
      return null;
    }
    return {
      code: parsed.code,
      name: parsed.name,
      role: normalizeAdminRole(parsed.role),
      adminId: parsed.adminId,
      organizationId: parsed.organizationId,
      organizationCode: parsed.organizationCode,
      organizationName: parsed.organizationName || parsed.organizationCode,
    };
  } catch {
    return null;
  }
}
