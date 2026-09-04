-- WP/TRK di missione per ente (sostituisce lo Google Sheet).
-- Admin generale: Storage bucket mission-gps + riga in mission_gps_files.
-- L'app legge solo i file is_enabled dell'ente (e dell'evento, se event_id è valorizzato).
-- Eseguire dopo sql/organizations.sql.

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'mission-gps',
  'mission-gps',
  false,
  8388608,
  null
)
on conflict (id) do update set
  public = excluded.public,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

create table if not exists mission_gps_files (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references organizations(id) on delete restrict,
  event_id uuid references events(id) on delete set null,
  kind text not null check (kind in ('wpt', 'trk')),
  file_name text not null,
  storage_path text not null,
  is_enabled boolean not null default true,
  created_at timestamptz not null default now(),
  constraint mission_gps_files_name_unique unique (organization_id, kind, file_name)
);

create index if not exists mission_gps_files_org_idx
  on mission_gps_files (organization_id, kind, is_enabled);

alter table mission_gps_files enable row level security;

drop policy if exists "gest anon read mission_gps_files" on mission_gps_files;
create policy "gest anon read mission_gps_files"
  on mission_gps_files for select to anon using (true);

drop policy if exists "anon select mission-gps" on storage.objects;
create policy "anon select mission-gps"
on storage.objects for select to anon
using (bucket_id = 'mission-gps');

comment on table mission_gps_files is
  'Catalogo WP/TRK missione. Upload file in Storage mission-gps, poi insert qui (org_code path es. NVANSMI/AREA.trk).';

-- Esempio (dopo aver caricato il file in Storage → mission-gps):
-- insert into mission_gps_files (organization_id, kind, file_name, storage_path)
-- select id, 'trk', 'OULX_PELLUS', 'NVANSMI/OULX_PELLUS.trk'
-- from organizations where org_code = 'NVANSMI';
