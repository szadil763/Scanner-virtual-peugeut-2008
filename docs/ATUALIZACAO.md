# Atualização pelo app

Ajustes > **Atualização do app**: mostra a versão instalada, o canal e o botão **Procurar atualização**. Ao abrir, o app confere sozinho (no máximo a cada 6 horas) e, se houver versão nova, mostra um aviso no alto de todas as abas com o botão para baixar e instalar.

## Canais

| Canal | Release do GitHub | Quem usa |
|---|---|---|
| Teste | `latest` (todo push na branch gera um APK) | O 2008 do dono, para testar antes |
| Estável | `estavel` (só o que o dono aprovou) | Os outros carros; é o padrão de uma instalação nova |

O aparelho que já rodou com o adaptador antes desta versão (o 2008 do dono) começa no canal Teste; instalação nova começa no Estável. Dá para trocar em Ajustes.

## Aprovar uma versão (teste → estável)

Depois de testar no carro: GitHub > Actions > **Publicar versão estável** > Run workflow (`.github/workflows/publish-stable.yml`). Ele copia o APK da release `latest` para a `estavel`. Os carros no canal Estável recebem na próxima conferência.

## Como funciona

- `data/Updater` lê a release do canal pela API do GitHub (`/releases/tags/<canal>`), tira o `versionCode` das notas (o workflow escreve `versionCode: N`; regras puras e testadas em `domain/Update.kt`) e compara com o instalado.
- Baixa o `monitor-2008.apk` para `cache/updates` e abre a instalação do Android (`FileProvider`, autoridade `<pacote>.updates`).
- O Android sempre pede a confirmação "Instalar"; na primeira vez, pede para permitir que o Monitor instale apps (botão "Permitir instalar atualizações"). Sem Play Store não dá para instalar sem esse toque.
- Os dados continuam porque todo APK é assinado com a mesma chave. Até a troca abaixo, é a `android/app/debug.keystore` (chave 1); depois, a chave 2, guardada em segredos do GitHub.
- Só com o carro parado.

## Troca da chave de assinatura (chave 1 → chave 2)

A chave 1 (`android/app/debug.keystore`, senha `android`) está no repositório público: qualquer pessoa consegue gerar um APK que o Android aceita como atualização do Monitor. A chave 2 fica só nos segredos do GitHub Actions.

O Android **não instala por cima** um APK de outra chave ("conflito com pacote existente"). Por isso a troca tem uma versão de transição:

- Cada release diz nas notas qual chave assinou o APK (`chave: 1` ou `chave: 2`); o app sabe a própria (`BuildConfig.CHAVE_ASSINATURA`).
- Versão nova com outra chave não aparece como "Baixar e instalar": o app (Ajustes > Atualização, aviso no alto das abas e Ajustes do painel) mostra o passo a passo de reinstalar (`UpdateState.NeedsReinstall`, `UpdateRules.needsReinstall`, com testes).
- Sem os segredos, o build continua com a chave 1; com eles, assina com a chave 2 (`build.gradle.kts`, passo "Chave de assinatura" do `build-apk.yml`).

### Ordem

1. **Instalar a versão de transição em todos os aparelhos** (multimídias e celulares), ainda com a chave 1, pela atualização normal, e **publicá-la também como Estável** (workflow "Publicar versão estável"), para os carros desse canal. Sem ela, o app antigo tenta instalar a versão nova por cima e o Android só diz "app não instalado".
2. **Gerar a chave 2** num computador com Java (o Android Studio já traz o `keytool` em `jbr/bin`):

   ```bash
   keytool -genkeypair -v -keystore monitor.jks -alias monitor -keyalg RSA -keysize 4096 -validity 10000
   ```

   Ele pede uma senha (guarde) e nome/cidade (pode responder qualquer coisa). **Guarde o `monitor.jks` e a senha em lugar seguro, fora do repositório** (ex.: gerenciador de senhas e uma cópia num pendrive). Sem eles, nenhuma atualização instala mais por cima.
3. **Transformar o arquivo em texto** para o segredo:

   ```bash
   base64 -w0 monitor.jks > monitor.jks.txt
   ```

   No Windows (PowerShell): `[Convert]::ToBase64String([IO.File]::ReadAllBytes("monitor.jks")) > monitor.jks.txt`
4. **Criar os segredos** no GitHub (precisa ser administrador do repositório): Settings > Secrets and variables > Actions > New repository secret.

   | Nome | Valor |
   |---|---|
   | `SIGNING_KEYSTORE_BASE64` | conteúdo do `monitor.jks.txt` |
   | `SIGNING_STORE_PASSWORD` | a senha do passo 2 |
   | `SIGNING_KEY_ALIAS` | `monitor` |
   | `SIGNING_KEY_PASSWORD` | a mesma senha do passo 2 |

   Depois, apague o `monitor.jks.txt`.
5. **Gerar a primeira versão com a chave 2**: Actions > Gerar APK > Run workflow (ou o próximo envio para a branch). O log do passo "Chave de assinatura" diz "Assinando com a chave 2" e as notas da release terminam em `chave: 2`.
6. **Em cada aparelho**, com o carro parado: Ajustes > Conta Google e nuvem > Sincronizar este carro; baixar o APK pela página da release; desinstalar o Monitor; instalar o APK; entrar na mesma conta e sincronizar. O registro da tensão da bateria e as preferências só do aparelho (`NAO_VAI_PARA_NUVEM`) não voltam.
7. **Depois que todos estiverem na chave 2**: apagar a `android/app/debug.keystore` do repositório e o caminho de reserva no `build.gradle.kts` (sem os segredos, o build local passa a usar a chave de debug do próprio Android Studio).
8. **Canal Estável**: só publicar como estável uma versão da chave 2 depois de reinstalar nos carros que usam esse canal; eles recebem o mesmo aviso.
