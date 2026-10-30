-- =====================================================================
-- Caixa Comum — banco de dados
-- Como usar: no Supabase, abra "SQL Editor", cole TODO este arquivo
-- e clique em "Run". Pode rodar de novo sem problema.
-- =====================================================================

-- ---------- Tabelas ----------

-- Um "caixa" é o conjunto de finanças compartilhado (ex.: você e sua esposa)
create table if not exists public.caixas (
  id         uuid primary key default gen_random_uuid(),
  nome       text not null,
  codigo     text unique not null default upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 6)),
  criado_por uuid not null default auth.uid() references auth.users(id) on delete cascade,
  criado_em  timestamptz not null default now()
);

-- Quem participa de cada caixa
create table if not exists public.membros (
  caixa_id  uuid not null references public.caixas(id) on delete cascade,
  user_id   uuid not null references auth.users(id) on delete cascade,
  nome      text,
  entrou_em timestamptz not null default now(),
  primary key (caixa_id, user_id)
);

-- Todos os registros do app (contas, cartões, categorias, lançamentos,
-- orçamento e caixa de entrada de notificações), um por linha
create table if not exists public.docs (
  caixa_id      uuid not null references public.caixas(id) on delete cascade,
  col           text not null check (col in ('accounts','cards','categories','tx','inbox','budgets')),
  id            text not null,
  data          jsonb not null default '{}'::jsonb,
  atualizado_em timestamptz not null default now(),
  primary key (caixa_id, col, id)
);
create index if not exists docs_caixa_idx on public.docs (caixa_id);

-- Celulares Android autorizados a mandar notificações para um caixa
create table if not exists public.dispositivos (
  token     text primary key default (replace(gen_random_uuid()::text,'-','') || replace(gen_random_uuid()::text,'-','')),
  caixa_id  uuid not null references public.caixas(id) on delete cascade,
  user_id   uuid not null references auth.users(id) on delete cascade,
  nome      text,
  criado_em timestamptz not null default now(),
  visto_em  timestamptz
);

-- ---------- Regras de acesso ----------

create or replace function public.eh_membro(c uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from membros where caixa_id = c and user_id = auth.uid());
$$;

alter table public.caixas       enable row level security;
alter table public.membros      enable row level security;
alter table public.docs         enable row level security;
alter table public.dispositivos enable row level security;

drop policy if exists caixas_ler on public.caixas;
create policy caixas_ler on public.caixas for select using (public.eh_membro(id));
drop policy if exists caixas_renomear on public.caixas;
create policy caixas_renomear on public.caixas for update using (public.eh_membro(id)) with check (public.eh_membro(id));

drop policy if exists membros_ler on public.membros;
create policy membros_ler on public.membros for select using (public.eh_membro(caixa_id));
drop policy if exists membros_meu_nome on public.membros;
create policy membros_meu_nome on public.membros for update using (user_id = auth.uid()) with check (user_id = auth.uid());
drop policy if exists membros_sair on public.membros;
create policy membros_sair on public.membros for delete using (user_id = auth.uid());

drop policy if exists docs_tudo on public.docs;
create policy docs_tudo on public.docs for all
  using (public.eh_membro(caixa_id)) with check (public.eh_membro(caixa_id));

drop policy if exists disp_ler on public.dispositivos;
create policy disp_ler on public.dispositivos for select using (public.eh_membro(caixa_id));
drop policy if exists disp_apagar on public.dispositivos;
create policy disp_apagar on public.dispositivos for delete using (public.eh_membro(caixa_id));

-- ---------- Funções usadas pelo app ----------

-- Cria um caixa novo, já com categorias padrão e a conta Carteira
create or replace function public.criar_caixa(p_nome text, p_meu_nome text)
returns public.caixas language plpgsql security definer set search_path = public as $$
declare c caixas;
begin
  if auth.uid() is null then raise exception 'Faça login primeiro.'; end if;
  insert into caixas (nome, criado_por) values (coalesce(nullif(trim(p_nome), ''), 'Nosso caixa'), auth.uid()) returning * into c;
  insert into membros (caixa_id, user_id, nome) values (c.id, auth.uid(), p_meu_nome);
  insert into docs (caixa_id, col, id, data)
  select c.id, 'categories', v.id, jsonb_build_object('name', v.nome, 'kind', v.tipo, 'color', v.cor)
  from (values
    ('alimentacao','Alimentação','despesa','#C2410C'),
    ('mercado','Mercado','despesa','#2E7D6B'),
    ('transporte','Transporte','despesa','#2563EB'),
    ('moradia','Moradia','despesa','#9333EA'),
    ('contas','Contas e serviços','despesa','#0E7490'),
    ('saude','Saúde','despesa','#DC2626'),
    ('educacao','Educação','despesa','#4D7C0F'),
    ('lazer','Lazer','despesa','#DB2777'),
    ('assinaturas','Assinaturas','despesa','#B7791F'),
    ('compras','Compras','despesa','#475569'),
    ('pix-pessoas','Pix e transferências','despesa','#64748B'),
    ('outros-gastos','Outros gastos','despesa','#78716C'),
    ('salario','Salário','receita','#1B8A4C'),
    ('pix-recebido','Pix e transferências recebidas','receita','#0E7490'),
    ('rendimentos','Rendimentos','receita','#B7791F'),
    ('outras-receitas','Outras receitas','receita','#475569')
  ) as v(id, nome, tipo, cor);
  insert into docs (caixa_id, col, id, data) values
    (c.id, 'accounts', 'carteira', jsonb_build_object('name','Carteira','bank','outro','type','carteira','initial',0,'color','#4D7C0F','archived',false));
  return c;
end $$;

-- Entra num caixa existente usando o código de convite
create or replace function public.entrar_caixa(p_codigo text, p_meu_nome text)
returns public.caixas language plpgsql security definer set search_path = public as $$
declare c caixas;
begin
  if auth.uid() is null then raise exception 'Faça login primeiro.'; end if;
  select * into c from caixas where codigo = upper(trim(p_codigo));
  if not found then raise exception 'Código não encontrado. Confira as 6 letras e números.'; end if;
  insert into membros (caixa_id, user_id, nome) values (c.id, auth.uid(), p_meu_nome)
  on conflict (caixa_id, user_id) do update set nome = coalesce(excluded.nome, membros.nome);
  return c;
end $$;

-- Gera a chave que o celular Android usa para mandar notificações
create or replace function public.criar_dispositivo(p_caixa uuid, p_nome text)
returns text language plpgsql security definer set search_path = public as $$
declare t text;
begin
  if not eh_membro(p_caixa) then raise exception 'Você não participa deste caixa.'; end if;
  insert into dispositivos (caixa_id, user_id, nome) values (p_caixa, auth.uid(), p_nome) returning token into t;
  return t;
end $$;

-- Chamada pelo app Android a cada notificação de banco.
-- Não exige login: vale a chave do dispositivo (que pode ser removida no app).
create or replace function public.registrar_notificacao(p_token text, p_texto text, p_banco text, p_app text)
returns boolean language plpgsql security definer set search_path = public as $$
declare d dispositivos;
begin
  select * into d from dispositivos where token = p_token;
  if not found then return false; end if;
  if p_texto is null or length(trim(p_texto)) < 5 then return true; end if;
  -- ignora a mesma notificação repetida nos últimos 30 minutos
  if exists (select 1 from docs where caixa_id = d.caixa_id and col = 'inbox'
             and data->>'raw' = left(p_texto, 600) and atualizado_em > now() - interval '30 minutes') then
    return true;
  end if;
  insert into docs (caixa_id, col, id, data) values (
    d.caixa_id, 'inbox', 'n' || replace(gen_random_uuid()::text, '-', ''),
    jsonb_build_object(
      'raw', left(p_texto, 600),
      'bank', nullif(p_banco, ''),
      'app', p_app,
      'status', 'pendente',
      'source', 'android',
      'createdBy', d.user_id::text,
      'createdAt', to_char(now() at time zone 'utc', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')
    ));
  update dispositivos set visto_em = now() where token = p_token;
  return true;
end $$;

revoke execute on function public.registrar_notificacao(text, text, text, text) from public;
grant execute on function public.registrar_notificacao(text, text, text, text) to anon, authenticated;
grant execute on function public.criar_caixa(text, text), public.entrar_caixa(text, text), public.criar_dispositivo(uuid, text) to authenticated;

-- ---------- Tempo real (o que um lança aparece na hora para o outro) ----------
do $$ begin
  alter publication supabase_realtime add table public.docs;
exception when duplicate_object then null; end $$;
