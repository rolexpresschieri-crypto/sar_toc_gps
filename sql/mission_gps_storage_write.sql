-- Scrittura del bucket mission-gps dal TOC (anon).
-- L'app continua a leggere solo i .trk e .wpt sotto {ENTE}/.
-- Eseguire in Supabase → SQL Editor dopo sql/mission_gps.sql.

drop policy if exists "anon insert mission-gps" on storage.objects;
create policy "anon insert mission-gps"
on storage.objects for insert to anon
with check (bucket_id = 'mission-gps');

drop policy if exists "anon update mission-gps" on storage.objects;
create policy "anon update mission-gps"
on storage.objects for update to anon
using (bucket_id = 'mission-gps')
with check (bucket_id = 'mission-gps');

drop policy if exists "anon delete mission-gps" on storage.objects;
create policy "anon delete mission-gps"
on storage.objects for delete to anon
using (bucket_id = 'mission-gps');
