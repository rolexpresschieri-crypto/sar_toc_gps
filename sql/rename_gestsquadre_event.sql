-- Toglie «gestSQUADRE» / «Evento operativo» dai titoli visibili in TOC e app.
-- Eseguire in Supabase → SQL Editor.

update events
set
  title = 'Operazione demo TOC SAR',
  description = 'Demo'
where title ilike '%gestSQUADRE%'
   or title ilike '%Evento operativo gestSQUADRE%';
