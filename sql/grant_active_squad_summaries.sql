-- Accesso lettura operatori online (mappa telefono + TOC).
-- Eseguire se GET /active_squad_summaries fallisce con permission denied.

grant select on active_squad_summaries to anon, authenticated, service_role;
