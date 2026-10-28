# Monitor 2008 no Android Auto

Com o APK instalado **no celular**, o Monitor 2008 aparece na tela do carro quando o Android Auto conecta.

## Como funciona

- O app no celular conecta no adaptador (OBDBLE) e o Android Auto mostra os dados na tela do multimídia.
- Usa a **Car App Library** do Google (`car/`): o Android Auto só aceita telas de modelo (grade, lista), não telas livres. Por isso:
  - **Painel**: grade com 6 blocos (velocidade, consumo, rotação, temperatura, média da viagem, bateria). Os números grandes são desenhados como imagem; atualiza 1 vez por segundo.
  - **Saúde**: lista dos sistemas com o status (alertas primeiro). Gráficos e explicações completas ficam na tela do app no celular.
- A tela do app e o Android Auto usam a **mesma conexão** (`MonitorEngine`, um por processo), porque o adaptador aceita uma conexão por vez.

## Como ativar (uma vez)

1. Instale o APK no celular e abra o Monitor 2008: aceite as permissões e, em **Ajustes**, ligue o adaptador (desligue "dados simulados").
2. No celular, abra as configurações do **Android Auto** (Configurações → Apps → Android Auto → Configurações adicionais no app, ou pelo ícone do Android Auto).
3. Toque **10 vezes** em "Versão" (no fim da tela) até aparecer "Configurações do desenvolvedor".
4. No menu ⋮ → **Configurações do desenvolvedor** → ligue **Fontes desconhecidas**. Sem isso, o Android Auto só mostra apps da Play Store.
5. Conecte no carro. O Monitor 2008 aparece na lista de apps do Android Auto (ícone de apps).

## Limitações (por que não é igual à tela do app)

- **Só modelos do Google**: sem gráficos grandes nem painel livre. A grade de 6 blocos é o máximo permitido dirigindo.
- **Sem tela dividida com o mapa dentro do Android Auto**: a tela dividida do Android Auto só aceita mapas, música e mensagens. Você alterna entre o mapa e o Monitor pela barra do Android Auto.
- **Uma conexão por vez no adaptador**: se o Monitor também estiver aberto no multimídia (fora do Android Auto), um dos dois não conecta. No multimídia, deixe o Monitor fechado ou em "dados simulados".
- **Categoria "IoT"**: o app se declara como app de dispositivo conectado (a categoria mais próxima). Só funciona instalado por APK com "Fontes desconhecidas"; a Play Store não aceitaria um painel de carro nessa categoria.
- O multimídia recebe o Android Auto por um app de espelhamento (ex.: ZLink/AutoKit). Se esse app não suportar apps da Car App Library, o Monitor não aparece; nesse caso, use o app direto no multimídia.

## Testar sem o carro

O Google tem o **Desktop Head Unit (DHU)**, que simula a tela do carro no computador: Android Studio → SDK Manager → SDK Tools → "Android Auto Desktop Head Unit Emulator".
