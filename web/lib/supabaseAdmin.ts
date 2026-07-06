import { createClient } from '@supabase/supabase-js';

// Server-only admin client (service role). Never import this into a client component.
export function adminClient() {
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!url || !serviceKey) throw new Error('Missing SUPABASE env (URL / SERVICE_ROLE_KEY).');
  return createClient(url, serviceKey, { auth: { autoRefreshToken: false, persistSession: false } });
}
