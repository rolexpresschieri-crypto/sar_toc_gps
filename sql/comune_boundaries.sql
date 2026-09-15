-- Confini comunali GeoJSON (gestiti dalla TOC Anagrafica, letti da sala e app).
-- Eseguire in Supabase → SQL Editor.

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'comune-boundaries',
  'comune-boundaries',
  false,
  4194304,
  null
)
on conflict (id) do update set
  public = excluded.public,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "anon select comune-boundaries" on storage.objects;
create policy "anon select comune-boundaries"
on storage.objects for select to anon
using (bucket_id = 'comune-boundaries');

drop policy if exists "anon insert comune-boundaries" on storage.objects;
create policy "anon insert comune-boundaries"
on storage.objects for insert to anon
with check (bucket_id = 'comune-boundaries');

drop policy if exists "anon update comune-boundaries" on storage.objects;
create policy "anon update comune-boundaries"
on storage.objects for update to anon
using (bucket_id = 'comune-boundaries')
with check (bucket_id = 'comune-boundaries');

drop policy if exists "anon delete comune-boundaries" on storage.objects;
create policy "anon delete comune-boundaries"
on storage.objects for delete to anon
using (bucket_id = 'comune-boundaries');
