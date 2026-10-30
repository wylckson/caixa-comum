# Caixa Comum — app Android + versão web

Controle de finanças compartilhado, no estilo do Organizze. O app Android lê as notificações de **Banco do Brasil, PicPay, C6 Bank, Inter e Nubank** e manda para a caixa de entrada. A versão web e o app usam os mesmos dados, em tempo real, para você e quem divide o caixa com você.

O que tem nesta pasta:

| Pasta | O que é |
|---|---|
| `supabase/schema.sql` | O banco de dados (você cola uma vez no Supabase) |
| `web/` | O site do Caixa Comum (o app Android abre este mesmo site) |
| `android/` | O app Android com a captura de notificações |
| `.github/workflows/` | Automação: publica o site e gera o APK sozinho |

O passo a passo leva uns 20 minutos e é feito uma vez só.

---

## 1. Criar o banco no Supabase (grátis)

1. Entre em **supabase.com**, clique em **Start your project** e crie a conta (pode usar o login do GitHub).
2. Clique em **New project**. Dê o nome `caixa-comum`, crie uma senha qualquer (guarde) e escolha a região **South America (São Paulo)**. Clique em **Create new project** e espere uns 2 minutos.
3. No menu da esquerda, abra **SQL Editor**. Abra o arquivo `supabase/schema.sql` desta pasta, copie tudo, cole no editor e clique em **Run**. Deve aparecer "Success".
4. Para simplificar o cadastro, vá em **Authentication › Sign In / Providers › Email** e desligue **Confirm email**. Assim a conta funciona na hora, sem precisar abrir o e-mail de confirmação. (Se preferir manter ligado, tudo funciona, só tem esse passo a mais.)
5. Vá em **Project Settings › API** (ou **Data API**) e deixe aberto. Você vai copiar dois dados:
   - **Project URL** (algo como `https://abcdxyz.supabase.co`)
   - **anon public** key (um texto longo que começa com `eyJ`)

## 2. Colocar o projeto no GitHub (grátis)

1. Entre em **github.com** e crie a conta, se não tiver.
2. Clique em **+ › New repository**. Nome: `caixa-comum`. Marque **Public**. Clique em **Create repository**.
3. Na página do repositório vazio, clique em **uploading an existing file**.
4. Descompacte o arquivo que eu enviei e **arraste o conteúdo da pasta** (as pastas `android`, `supabase`, `web`, `.github` e este arquivo) para a página. Clique em **Commit changes**.

> **A pasta `.github` não apareceu?** No Mac e em alguns Windows ela fica oculta. Depois do envio, se não houver a pasta `.github` no repositório, clique em **Add file › Create new file**, digite o nome `.github/workflows/android.yml`, cole o conteúdo do arquivo de mesmo nome e salve. Repita para `.github/workflows/pages.yml`.

## 3. Ligar o site

1. No repositório, vá em **Settings › Pages**. Em **Source**, escolha **GitHub Actions**.
2. Volte para o repositório, abra a pasta `web` e o arquivo `config.js`. Clique no lápis (editar) e troque:
   - `COLE_AQUI_A_PROJECT_URL` pela **Project URL** do Supabase
   - `COLE_AQUI_A_CHAVE_ANON_PUBLIC` pela chave **anon public**
3. Clique em **Commit changes**. Em 1 ou 2 minutos o site fica no ar em:
   **`https://SEU-USUARIO.github.io/caixa-comum/`**
   (em **Actions** dá para acompanhar; quando aparecer o visto verde, está pronto)

> A chave anon é pública por natureza: o banco só mostra os dados para quem fez login e participa do caixa.

## 4. Instalar o app no Android

1. O APK é gerado sozinho depois do envio (aba **Actions › Gerar APK**, uns 5 minutos).
2. **No celular**, abra `https://github.com/SEU-USUARIO/caixa-comum/releases`, toque na versão mais nova e baixe **CaixaComum.apk**.
3. Abra o arquivo baixado. O Android vai pedir para permitir instalar apps desta fonte: permita e toque em **Instalar**.
4. Abra o Caixa Comum, toque em **Criar conta**, depois **Criar caixa** (ex.: "Casa").
5. Vá em **Importar** e toque em **Liberar acesso às notificações**. Ative o **Caixa Comum** e confirme.

> **Android 13 ou mais novo:** se a opção do Caixa Comum aparecer cinza, faça assim: **Configurações › Apps › Caixa Comum › ⋮ (três pontos, no canto) › Permitir configurações restritas**. Depois volte e ative o acesso às notificações.
>
> **Para não perder notificações:** em **Configurações › Apps › Caixa Comum › Bateria**, escolha **Sem restrições**.

Pronto: cada compra, Pix ou recebimento que os apps dos bancos notificarem cai em **Importar › Caixa de entrada**. Você confere, ajusta a categoria e toca em **Lançar**.

## 5. Usar junto com outra pessoa

1. No seu app, abra **Mais › Categorias e pessoas** e copie o **código de convite** (6 caracteres).
2. A outra pessoa abre o site (ou instala o APK, se tiver Android), cria a conta dela, toca em **Entrar no caixa** e digita o código.
3. Se ela tiver Android, o celular dela também passa a mandar as notificações dos bancos dela para o mesmo caixa.

**iPhone:** abra o site no Safari, toque em **Compartilhar › Adicionar à Tela de Início**. Ele vira um ícone igual a um app. O iPhone não permite ler notificações de outros apps, então ali os avisos são colados em **Importar**.

---

## Atualizações

Sempre que um arquivo da pasta `android` mudar no GitHub, um APK novo aparece em **Releases**. Instale por cima do antigo: os dados ficam no Supabase, nada se perde. Mudanças na pasta `web` aparecem no site e no app sozinhas, sem reinstalar.

## Problemas comuns

| O que aparece | O que fazer |
|---|---|
| Tela "Conectar ao Supabase" no site | O `config.js` não foi preenchido. Refaça o passo 3.2 (ou cole URL e chave na própria tela). |
| "Não foi possível conectar ao banco" | O `schema.sql` não rodou. Refaça o passo 1.3. |
| App abre a tela "Sem conexão" | Confira a internet. Se persistir, veja se o site abre no navegador do celular. |
| Notificações não chegam | Confira o acesso às notificações (passo 4.5), a bateria sem restrições e, em **Categorias e pessoas › Celulares conectados**, se o celular aparece. |
| O APK não aparece em Releases | Em **Actions**, abra **Gerar APK**. Se estiver vermelho, clique em **Re-run jobs**. |

## Apps reconhecidos

| Banco | Pacote do app no Android |
|---|---|
| Banco do Brasil | `br.com.bb.android` |
| PicPay | `com.picpay` |
| C6 Bank | `com.c6bank.app` |
| Inter | `br.com.intermedium` |
| Nubank | `com.nu.production` |

Para incluir outro banco, acrescente o pacote dele na lista `BANKS` do arquivo `android/app/src/main/java/br/caixacomum/app/BankNotificationService.kt`.
