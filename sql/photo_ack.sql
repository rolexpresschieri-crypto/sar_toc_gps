-- Notifiche foto su TOC: spegnimento (come gli allarmi).
-- Eseguire in Supabase → SQL Editor.

alter table squad_field_photo_logs
  add column if not exists acknowledged_at timestamptz;

alter table squad_field_photo_logs
  add column if not exists acknowledged_by text;

drop policy if exists "gest anon update squad_field_photo_logs" on squad_field_photo_logs;
create policy "gest anon update squad_field_photo_logs"
  on squad_field_photo_logs for update to anon using (true) with check (true);
