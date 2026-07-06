'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { supabase } from './supabaseClient';

export type Profile = {
  id: string;
  role: 'student' | 'staff' | 'admin';
  full_name: string;
  email: string | null;
  phone: string | null;
  department_id: string | null;
  avatar_url: string | null;
};

/**
 * Loads the signed-in user's profile. If there is no session, redirects to /login.
 * If `allowed` is given and the role doesn't match, redirects to that role's home.
 */
export function useProfile(allowed?: Profile['role'][]) {
  const router = useRouter();
  const [profile, setProfile] = useState<Profile | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const { data: sess } = await supabase.auth.getSession();
      if (!sess.session) {
        router.replace('/login');
        return;
      }
      const { data, error } = await supabase.from('profiles').select('*').eq('id', sess.session.user.id).single();
      if (cancelled) return;
      if (error || !data) {
        await supabase.auth.signOut();
        router.replace('/login');
        return;
      }
      if (allowed && !allowed.includes(data.role)) {
        router.replace(`/${data.role}`);
        return;
      }
      setProfile(data as Profile);
      setLoading(false);
    })();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function signOut() {
    await supabase.auth.signOut();
    router.replace('/login');
  }

  return { profile, loading, signOut };
}
