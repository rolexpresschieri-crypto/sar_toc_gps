-- Enti / associazioni (multi-tenant silenzioso).
-- Codice ente sempre MAIUSCOLO (es. NVANSMI). Il login app resta operatore + password:
-- l'app scrive organization_id su sessioni, allarmi, foto e log auth.
-- Eseguire in Supabase → SQL Editor dopo schema_v1.sql (e dopo squad_field_photos /
-- squad_session_auth_logs se quelle tabelle esistono già).

create table if not exists organizations (
  id uuid primary key default gen_random_uuid(),
  org_code text not null unique,
  org_name text not null,
  is_enabled boolean not null default true,
  created_at timestamptz not null default now(),
  constraint organizations_org_code_upper check (org_code = upper(org_code)),
  constraint organizations_org_code_format check (org_code ~ '^[A-Z0-9_]+$')
);

create or replace function organizations_force_uppercase()
returns trigger
language plpgsql
as $$
begin
  new.org_code := upper(trim(new.org_code));
  return new;
end;
$$;

drop trigger if exists organizations_force_uppercase_trg on organizations;
create trigger organizations_force_uppercase_trg
before insert or update of org_code on organizations
for each row execute procedure organizations_force_uppercase();

insert into organizations (org_code, org_name)
select 'NVANSMI', 'NVANSMI'
where not exists (select 1 from organizations where org_code = 'NVANSMI');

alter table organizations enable row level security;

drop policy if exists "gest anon all organizations" on organizations;
create policy "gest anon all organizations"
  on organizations for all to anon using (true) with check (true);

-- Colonne + backfill sull'ente seed, poi NOT NULL.
do $$
declare
  v_org uuid;
begin
  select id into v_org from organizations where org_code = 'NVANSMI';
  if v_org is null then
    raise exception 'Seed NVANSMI mancante';
  end if;

  alter table events
    add column if not exists organization_id uuid references organizations(id) on delete restrict;
  alter table squads
    add column if not exists organization_id uuid references organizations(id) on delete restrict;
  alter table squad_sessions
    add column if not exists organization_id uuid references organizations(id) on delete restrict;
  alter table squad_alarms
    add column if not exists organization_id uuid references organizations(id) on delete restrict;

  update events set organization_id = v_org where organization_id is null;
  update squads set organization_id = v_org where organization_id is null;
  update squad_sessions set organization_id = v_org where organization_id is null;
  update squad_alarms set organization_id = v_org where organization_id is null;

  alter table events alter column organization_id set not null;
  alter table squads alter column organization_id set not null;
  alter table squad_sessions alter column organization_id set not null;
  alter table squad_alarms alter column organization_id set not null;

  if exists (
    select 1 from information_schema.tables
    where table_schema = 'public' and table_name = 'squad_field_photo_logs'
  ) then
    alter table squad_field_photo_logs
      add column if not exists organization_id uuid references organizations(id) on delete restrict;
    update squad_field_photo_logs set organization_id = v_org where organization_id is null;
    alter table squad_field_photo_logs alter column organization_id set not null;
  end if;

  if exists (
    select 1 from information_schema.tables
    where table_schema = 'public' and table_name = 'squad_session_auth_logs'
  ) then
    alter table squad_session_auth_logs
      add column if not exists organization_id uuid references organizations(id) on delete restrict;
    update squad_session_auth_logs set organization_id = v_org where organization_id is null;
    alter table squad_session_auth_logs alter column organization_id set not null;
  end if;
end $$;

create index if not exists events_organization_idx on events (organization_id);
create index if not exists squads_organization_idx on squads (organization_id);
create index if not exists squad_sessions_organization_idx on squad_sessions (organization_id);
create index if not exists squad_alarms_organization_idx on squad_alarms (organization_id);

do $$
begin
  if exists (
    select 1 from information_schema.tables
    where table_schema = 'public' and table_name = 'squad_field_photo_logs'
  ) then
    execute 'create index if not exists squad_field_photo_logs_organization_idx on squad_field_photo_logs (organization_id)';
  end if;
  if exists (
    select 1 from information_schema.tables
    where table_schema = 'public' and table_name = 'squad_session_auth_logs'
  ) then
    execute 'create index if not exists squad_session_auth_logs_organization_idx on squad_session_auth_logs (organization_id)';
  end if;
end $$;

-- CREATE OR REPLACE non può rinominare/riordinare colonne: organization_id va in coda.
create or replace view active_squad_summaries as
select
  ss.id as session_id,
  ss.event_id,
  ss.squad_id,
  s.squad_code,
  s.squad_name,
  s.map_color,
  ss.is_online,
  ss.login_at,
  ss.last_latitude,
  ss.last_longitude,
  ss.last_accuracy,
  ss.last_fix_at,
  s.map_icon_key,
  ss.peer_visible,
  ss.organization_id
from squad_sessions ss
join squads s on s.id = ss.squad_id
where ss.is_online = true;

grant select on active_squad_summaries to anon, authenticated, service_role;

comment on table organizations is
  'Enti/associazioni. org_code sempre maiuscolo. Seed: NVANSMI.';
