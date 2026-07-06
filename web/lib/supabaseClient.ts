'use client';

import { createClient } from '@supabase/supabase-js';

// NEXT_PUBLIC_* are inlined at build time. The fallbacks only exist so a build
// without env vars doesn't crash; set the real values in Vercel before deploying.
const url = process.env.NEXT_PUBLIC_SUPABASE_URL || 'http://localhost:54321';
const anon = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || 'public-anon-key';

export const supabase = createClient(url, anon, {
  auth: { persistSession: true, autoRefreshToken: true },
});
