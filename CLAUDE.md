# CLAUDE.md

Contexto para continuar este projeto no Claude Code.

## O que é

App Android (Kotlin + Jetpack Compose) que lê o Peugeot 2008 2016 1.6 flex automático via adaptador ELM327 Bluetooth clássico e mostra consumo, velocidade, rotação, temperaturas, bateria e nível de combustível, mais controle de óleo e abastecimentos. Roda no multimídia Android 13 (1280x800) e em celulares.

Dono do projeto: Adilson. Idioma de UI, comentários e docs: português do Brasil.

## Comandos

```bash
node tools/verify-formulas.js        # confere fórmulas OBD e de consumo
cd android && gradle test            # testes unitários Kotlin (o Android Studio gera o ./gradlew ao importar)
cd android && gradle assembleDebug   # gera o APK de teste
```

O protótipo em `prototype/index.html` não tem build: basta abrir no navegador.

## Regras

1. **Somente leitura.** Só modos OBD 01 (dados atuais) e 03 (falhas armazenadas). Nunca enviar escrita, UDS, codificação nem modo 04 (apagar falhas) sem tela de confirmação pedida pelo dono. O desenho do botão de apagar, com gravidade por código, está em `docs/FALHAS.md` (fase 2, depois do teste real).
2. **Bluetooth clássico (RFCOMM/SPP, UUID 00001101-0000-1000-8000-00805F9B34FB) e BLE.** O adaptador do dono é duplo: aparece como `OBDII` (clássico) e `OBDBLE` (BLE). O pareamento clássico falhou no multimídia ("PIN não confere" com 1234, 0000, 6789 e 8888, sem pedir o PIN), então o app tenta o clássico pareado e, se não houver, procura o adaptador por BLE (`data/BleLink.kt`), que não usa PIN.
3. **Não inventar suporte a PID.** Use a consulta 0100/0120/0140/0160 e só leia o que o carro declarar. A tabela em `docs/PIDS.md` marca o que está confirmado e o que está a confirmar.
4. **Paridade de fórmulas.** Qualquer mudança em `obd/Pid.kt`, `obd/ObdParser.kt` ou `domain/FuelCalc.kt` deve ser refletida em `tools/verify-formulas.js` e `prototype/index.html`, com os mesmos números de teste.
5. **UI para multimídia:** alvos de toque grandes (mínimo 56 dp), fonte grande, alto contraste, sem animação que distraia. O app não pede digitação enquanto a velocidade é maior que 0.
6. O CANBOX Hiworld do multimídia não é fonte de dados (sem API aberta).
7. **Carro desligado = sem consulta.** Se a rotação ficar em 0 por alguns minutos (ou a conexão cair), parar de consultar o adaptador e desconectar; reconectar sozinho quando o motor ligar. Consultar com a ignição desligada mantém os módulos acordados e descarrega a bateria. O adaptador mini não dorme: recomendar tirá-lo se o carro ficar parado mais de ~7 dias. (Implementado em `BluetoothObdSource`, ainda não validado no carro: pausa de 60 s sem resposta de rotação, desliga após 5 min parado.)

## Decisões em aberto

- Quais PIDs o carro responde (MAF? MAP? 5E? 2F?). Só se sabe após o teste com o adaptador.
- Teor de etanol da gasolina comum: o padrão do app é 30% (E30). O governo aprovou aumento para 32%, então o valor é configurável.
- Eficiência volumétrica do motor (padrão 0,80) é chute inicial; ajustar com tanque cheio a tanque cheio.
- Intervalo de troca de óleo (padrão 10.000 km ou 12 meses): confirmar no manual do carro.
- Firebase para sincronizar histórico: opcional, fase 3 do roadmap.

## Estado

App compilado pelo GitHub Actions (`.github/workflows/build-apk.yml`, roda em `main` e na branch de desenvolvimento) e **testado no carro em 28/10/2026 (versão 0.1.6)**: conecta pelo BLE (`OBDBLE`) e o painel mostra rotação, velocidade, temperaturas e consumo pelo método MAP. O que o carro responde está em `docs/PIDS.md`: sem MAF, sem nível de combustível, sem PID 42 (a bateria vem do `ATRV`). Motor e câmbio respondem juntos às consultas do modo 01, por isso o parser junta as respostas de todas as centrais.

O APK sai em Releases (`latest`); a versão (0.1.N, N = número do build) aparece na tela Diagnóstico. Ver `docs/INSTALACAO.md`.

Tela **Saúde** (manutenção preventiva, etapas 1 e 2): grava um resumo por viagem e avalia mistura, sondas, catalisador, marcha lenta, borboleta, vácuo, arrefecimento, bateria e falhas (modos 03 e 07). Regras e limites em `docs/SAUDE.md`; código puro e testado em `domain/Health.kt`. A aba Manutenção tem itens por km/data (filtros, velas, correia, fluidos). Etapa 3 (previsões por tendência) depende de semanas de dados.

**Android Auto** (`car/`, `docs/ANDROID-AUTO.md`): com o APK no celular, o app aparece na tela do carro pela Car App Library (categoria IoT, exige "Fontes desconhecidas" no Android Auto). Só telas de modelo: grade de 6 blocos (números desenhados como imagem; título e blocos fixos para cada atualização contar como refresh) e lista da Saúde. Estado e conexão ficam em `ui/MonitorEngine` (um por processo), compartilhado entre a tela do app e o Android Auto, porque o adaptador aceita uma conexão por vez.

**Mapa e música** (recomendado em vez do Android Auto): barra no rodapé de todas as abas com "Mapa", "Música" e o mini player (`data/AppLauncher`). Com a tela já dividida, abre o app na outra metade (FLAG_ACTIVITY_LAUNCH_ADJACENT; abrir um troca pelo outro). Em tela cheia o multimídia do dono ignora o pedido de dividir (teste da 0.1.11), então o app abre em tela cheia e o Monitor vira janela flutuante. Mini player da música tocando (`data/MediaWatcher`) via sessões de mídia; exige "acesso a notificações" (`MediaListenerService`, que não lê as notificações). Rádio FM nativo do multimídia pode não aparecer.

Tela dividida e janela flutuante: abaixo de 800 dp de largura o app usa layout compacto; em janela pequena (picture-in-picture, ligado em Ajustes) mostra só velocidade, km/l, rotação e temperatura.

Também: hodômetro real (o dono informa o km do painel uma vez; `Store.odoOffset`, os km das trocas ficam gravados em km do app), preço do combustível em Ajustes, falhas lidas ao conectar e explicadas (`domain/DtcInfo`), relatório em texto para o mecânico (`domain/Report`, botão na Saúde, compartilha pelo WhatsApp) e serviço em primeiro plano (`data/ReadingService`, tipo connectedDevice) para seguir lendo com outro app em tela cheia. A margem direita é maior porque a moldura do multimídia cobre a borda direita da tela.

**Mídias** (`docs/MIDIAS.md`, `ui/MediaScreens.kt`): escolha **Música | Rádio** de um lado e os números do Painel do outro, sem tela dividida. Música: player grande (capa, tempo, botões grandes) do que estiver tocando e fontes (apps de música e os que já tocaram, como o Bluetooth do celular). Rádio: rádio pela internet tocando no próprio app (`data/RadioService` com Media3, precisa de `WAKE_LOCK`; lista do radio-browser.info, favoritas, da região, busca só parado).

**Postos** (`docs/POSTOS.md`): o abastecimento guarda o posto (GPS do multimídia + endereço pelo Geocoder, `data/PlaceFinder`) e o combustível; a nota de qualidade compara, por tanque cheio, litros reais ÷ previstos pelo ar, avanço de ignição e correção longa com a mediana dos tanques do mesmo combustível (`domain/FuelQuality`, com testes). Aba Postos com ranking e "Ir" (abre o mapa no posto).

**Vários carros** (`domain/Vehicle.kt`): perfil por carro (cilindrada para o método MAP, limites de consumo alto, o que verificar por indicador da Saúde, dicas de câmbio e revisão). Perfis: Peugeot 2008 1.6 EC5 e Citroën C4 Lounge 2.0 EW10 flex automático (segundo carro do dono, também com multimídia Android e adaptador próprio). Cada carro tem o app no próprio multimídia, com os próprios dados; o carro é escolhido na primeira abertura (instalação antiga com km rodados vale como 2008) e em Ajustes. Modelo novo = novo perfil em `Vehicles`.

**Atualização pelo app** (`docs/ATUALIZACAO.md`, `data/Updater`, `ui/UpdateScreens.kt`): botão em Ajustes e aviso no alto das abas; canais Teste (release `latest`) e Estável (release `estavel`, publicada pelo workflow manual `publish-stable.yml` depois de testar no carro). O Android sempre pede o toque em Instalar.

**Celular**: o app escolhe o layout pela tela: multimídia (menu lateral), tela dividida (compacto), celular (largura < 600 dp ou altura < 560 dp: abas embaixo com "Mais", telas em uma coluna, sem a barra de mapa/música) e janela flutuante.

Pendências conhecidas: registro de falhas com foto dos dados (freeze frame, modo 02), ícone do app, previsões por tendência (etapa 3 da Saúde), outros modelos além dos perfis (irmão e cunhado do dono, depois comercializar).
