-- Cartelle anagrafica (associazione / ente operativo).
-- NON è l'ente di login (organizations). Vale solo dentro l'ente TOC.
-- Un operatore appartiene a una sola cartella.
-- Eseguire in Supabase → SQL Editor DOPO sql/organizations.sql.

create table if not exists operator_folders (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references organizations(id) on delete restrict,
  folder_name text not null,
  map_color text not null default '#079B42',
  created_at timestamptz not null default now(),
  constraint operator_folders_name_not_blank check (length(trim(folder_name)) > 0)
);

create unique index if not exists operator_folders_org_name_uidx
  on operator_folders (organization_id, lower(trim(folder_name)));

create index if not exists operator_folders_organization_idx
  on operator_folders (organization_id);

alter table squads
  add column if not exists folder_id uuid references operator_folders(id) on delete restrict;

create index if not exists squads_folder_idx on squads (folder_id);

alter table operator_folders enable row level security;

drop policy if exists "gest anon all operator_folders" on operator_folders;
create policy "gest anon all operator_folders"
  on operator_folders for all to anon using (true) with check (true);

comment on table operator_folders is
  'Cartelle anagrafica associazione. Distinte da organizations (ente di login).';
