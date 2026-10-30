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
- Os dados continuam porque todo APK é assinado com a mesma chave (`android/app/debug.keystore`).
- Só com o carro parado.
