-- Policy Storage per upload foto da app (bucket già creato in squad_field_photos.sql).
-- Eseguire in Supabase → SQL Editor se INVIA FOTO fallisce con 403 / RLS.

drop policy if exists "anon insert squad-photos" on storage.objects;
create policy "anon insert squad-photos"
on storage.objects for insert to anon
with check (bucket_id = 'squad-photos');

drop policy if exists "anon update squad-photos" on storage.objects;
create policy "anon update squad-photos"
on storage.objects for update to anon
using (bucket_id = 'squad-photos')
with check (bucket_id = 'squad-photos');

drop policy if exists "anon select squad-photos" on storage.objects;
create policy "anon select squad-photos"
on storage.objects for select to anon
using (bucket_id = 'squad-photos');
