'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { supabase } from '@/lib/supabaseClient';
import { Spinner } from './ui/parts';

export default function Root() {
  const router = useRouter();
  useEffect(() => {
    (async () => {
      const { data } = await supabase.auth.getSession();
      if (!data.session) {
        router.replace('/login');
        return;
      }
      const { data: prof } = await supabase.from('profiles').select('role').eq('id', data.session.user.id).single();
      router.replace(prof?.role ? `/${prof.role}` : '/login');
    })();
  }, [router]);

  return (
    <div className="center-screen center">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src="/logo.png" alt="ClassKey" width={72} height={72} />
      <div className="h1 mt12">ClassKey</div>
      <div className="muted small">Secure attendance. Verified presence.</div>
      <div className="mt16">
        <Spinner blue />
      </div>
    </div>
  );
}
