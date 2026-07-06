-- ClassKey — 0004 storage buckets (avatars + proofs) and access policies
-- Run after 0001-0003.

insert into storage.buckets (id, name, public)
values ('avatars', 'avatars', true)
on conflict (id) do nothing;

insert into storage.buckets (id, name, public)
values ('proofs', 'proofs', false)
on conflict (id) do nothing;

-- avatars: public read; a user can write only into their own folder (avatars/<uid>/...)
drop policy if exists avatars_read on storage.objects;
create policy avatars_read on storage.objects for select to public
  using (bucket_id = 'avatars');

drop policy if exists avatars_write on storage.objects;
create policy avatars_write on storage.objects for insert to authenticated
  with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists avatars_update on storage.objects;
create policy avatars_update on storage.objects for update to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

-- proofs (request evidence): owner + staff/admin can read; owner writes own folder
drop policy if exists proofs_read on storage.objects;
create policy proofs_read on storage.objects for select to authenticated
  using (
    bucket_id = 'proofs' and (
      (storage.foldername(name))[1] = auth.uid()::text
      or public.jwt_role() in ('staff','admin')
    )
  );

drop policy if exists proofs_write on storage.objects;
create policy proofs_write on storage.objects for insert to authenticated
  with check (bucket_id = 'proofs' and (storage.foldername(name))[1] = auth.uid()::text);
