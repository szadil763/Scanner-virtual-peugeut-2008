# Comando de voz (primeira versão)

Botão **🎤 Voz**: na barra do rodapé (multimídia) e nas abas de baixo (celular). O dono toca, pergunta e o app responde em voz alta numa janela com letra grande. O microfone só liga no toque; não fica ouvindo.

## Perguntas que o app entende

Regras por palavra-chave em `domain/Voice.kt` (`VoiceCommands`, com testes), sem internet:

| Pergunta (exemplos) | Resposta |
|---|---|
| "qual o consumo", "quantos km por litro" | média do trajeto e, andando, o consumo do momento |
| "como está a viagem", "quanto andei" | km, tempo e média do trajeto |
| "temperatura do motor" | graus e se está normal, esquentando ou quente |
| "bateria" | volts e se está normal (com o motor ligado ou desligado) |
| "troca de óleo" | km e dias que faltam, ou vencida |
| "tem alguma falha", "luz de injeção" | códigos gravados, soletrados (P 0 4 2 0) |
| "como está o carro" | alertas e pontos de atenção da Saúde |
| "velocidade", "rotação" | valor atual |
| "ajuda" | lista de perguntas |

## Pedidos (fase 2)

| Pedido (exemplos) | O que faz |
|---|---|
| "abrir a saúde", "vai para o consumo", "mostra os postos", "voltar para o painel" | troca de aba (precisa de um verbo; só "rádio", "mapa" etc. também vale) |
| "abrir o rádio", "ouvir música" | aba Mídias no modo Rádio ou Música |
| "tocar rádio" / "tocar música" | além de abrir, toca a última rádio ou dá play na música |
| "abrir o mapa" | abre o app de mapa escolhido |
| "abasteci cem reais a seis e onze", "coloquei 40 litros a 6,11, não enchi", "abasteci 150 de etanol a 4,29, tanque cheio" | abre o registro de abastecimento **preenchido** (valor ou litros, preço, cheio/parcial, combustível); o dono confere o posto e toca em Salvar. Com o carro andando, não abre. |

## Rota, rádio pelo nome e música (fase 3)

| Pedido (exemplos) | O que faz |
|---|---|
| "ir para a rua Augusta 500", "navegar até o shopping Ibirapuera", "como chegar no posto Shell" | abre a rota no mapa escolhido (Waze e Google Maps já navegando; outros, na busca) |
| "ir para casa", "me leva pro trabalho" | usa o **lugar salvo** (Ajustes > Lugares salvos: nome e endereço) |
| "tocar rádio Jovem Pan", "toca a Band FM", "liga a rádio Transamérica" | procura nas favoritas e depois no radio-browser.info; toca e fala o nome achado |
| "tocar Péricles no Spotify", "toca a música Separação", "quero ouvir Até que durou" | pede ao app de música para buscar e tocar: pela sessão dele, sem tirá-lo do fundo (APK multimídia, com acesso a notificações); senão abre o app com a busca (`MEDIA_PLAY_FROM_SEARCH`). Spotify grátis pode tocar o artista no aleatório; Bluetooth do celular não aceita busca |

"Ir para" + nome de aba continua abrindo a aba ("ir para a saúde").

Números por extenso viram dígitos ("cento e vinte e cinco" = 125); "seis e onze" é o preço 6,11; "6 reais e 19 centavos" = 6,19. O preço é o número depois de "a", "por" ou antes de "o litro"; sem marca, o menor entre 1 e 20. Testes em `VoiceCommandsTest`.

## Como funciona no aparelho (`data/VoiceAssistant`)

- Reconhecimento de fala do próprio aparelho (`SpeechRecognizer`, pt-BR). Sem serviço para o app usar direto, tenta a tela de reconhecimento do sistema. Muitos multimídias chineses vêm sem o app Google: aí não há reconhecimento (alternativa futura: reconhecimento offline, ~50 MB).
- Resposta pelo leitor de voz do Android (`TextToSpeech`, pt-BR), como orientação de navegação: a música abaixa enquanto ouve e fala.
- **Voz do app** (Ajustes, `domain/TtsVoices` com testes): lista as vozes em português do leitor do aparelho como "Voz 1, 2..." (melhores primeiro: instalada, sem internet, maior qualidade), com Ouvir e Usar; velocidade e tom de 0,6 a 1,6. Sem escolha, usa a melhor. Mais vozes: instalar "Serviços de fala do Google" e baixar Português (Brasil).
- Ajustes > Comando de voz > **Testar este aparelho**: mostra se há microfone permitido, reconhecimento de voz e voz em português.
- Permissão `RECORD_AUDIO`, pedida no primeiro toque; `<queries>` para enxergar os serviços de reconhecimento e de voz.

## Permissões e configurações

Pedido do dono: onde faltar permissão, levar direto à tela do Android (`data/SystemSettings`). Microfone negado → tela do app; sem reconhecimento → app Google na Play Store; sem internet → Wi-Fi; poucas vozes → Baixar vozes, Saída de texto para fala e Serviços de fala do Google. O mesmo vale fora da voz: acesso a notificações (já na chave do Monitor no Android 11+; o mini player virou botão "Liberar a música tocando"), sobrepor a outros apps, localização, Bluetooth (negado de vez → tela do app).

Mapa pela voz: abre 0,5 s depois, quando a janela da conversa já fechou e o Monitor está em primeiro plano (vindo da tela de reconhecimento do sistema ele ainda estava pausado e não virava janela flutuante, 0.1.49). "Google Maps" e "mapas" também valem.

## Controle do que está tocando

"desligar o rádio", "pausar", "para a música" (mas "vai para a música" abre a tela), "próxima", "pula", "música anterior": age no rádio do app se ele estiver tocando (pausa, próxima/anterior entre as favoritas); senão no app de música pela sessão de mídia (APK do multimídia). Testes em `VoiceCommandsTest.mediaControls`.

## Sem tocar na tela: 🎤 da janela flutuante e a palavra "Monitor"

- **🎤 na janela flutuante própria** (`data/FloatingPanel`): pergunta ou pedido com o Monitor atrás do mapa; o título da janela mostra "Ouvindo..." e o que foi entendido; a resposta é falada.
- **"Monitor"** (Ajustes > Chamar dizendo "Monitor", desligado por padrão): reconhecimento offline Vosk (`com.alphacephei:vosk-android`), só com as palavras "monitor" e "[unk]" (`domain/Wake.kt`, com testes). O modelo pequeno em português (~40 MB, alphacephei.com) é baixado uma vez para `files/vosk-pt` (`data/WakeModel`). Ao ouvir: bipe, solta o microfone e abre o comando de voz normal.
- **Microfone em segundo plano** (`data/MicService`): o Android só deixa com serviço em primeiro plano do tipo microfone, iniciado com o app na frente (ao abrir o app ou ao mostrar a janela flutuante). Fica ligado com a palavra ligada ou a janela flutuante na tela. Para de escutar a palavra enquanto o comando de voz ouve ou fala e durante ligações (`AudioManager.mode`).
- **Pedidos sem a tela** (`data/VoiceActions`): rota, mapa, rádio, música, pausar/próxima funcionam com o Monitor atrás; abrir aba e abastecimento trazem o Monitor para a frente. Erros, sem a tela, são falados.
- APK só com bibliotecas ARM (`abiFilters` arm64-v8a e armeabi-v7a), por causa da biblioteca nativa do Vosk.

Ajustes do teste da 0.1.54 (às vezes demorava ou não captava o pedido): aciona pelo resultado parcial "monitor" (sem esperar a pausa); o bipe toca quando o reconhecimento já está pronto (antes tocava cedo e o começo do pedido se perdia); pausas curtas no meio do pedido não encerram a escuta (1,5 s); sem ouvir nada, pergunta "Pode repetir?" e ouve de novo uma vez.

Com o rádio ligado (relato da 0.1.55: difícil pegar o pedido): enquanto ouve o pedido o app pede o áudio "transitório" (o rádio do app e os outros apps PAUSAM, em vez de só abaixar) e devolve ao terminar de responder (voltam a tocar sozinhos). O que tocava antes é guardado (`radioWasPlaying`/`mediaWasPlaying`) para "desligar o rádio" pausar de verdade (`RadioPlayer.pause`, `MediaWatcher.pause`). A escuta da palavra usa microfone próprio (`AudioRecord`, fonte VOICE_RECOGNITION) com redutor de ruído e cancelamento de eco do aparelho, quando ele oferece.

Fora do carro (relato da 0.1.56: o celular seguia escutando e acionava até no WhatsApp): a escuta da palavra fica ativa com o carro conectado e por mais 15 minutos depois da última atividade (carro conectado, uso da voz, app aberto ou janela flutuante); depois o `MicService` para sozinho (solta o microfone, some a notificação). Volta quando o app conecta ao carro de novo (`MonitorEngine`) ou é aberto. Regra em `WakeRules.awake`, com testes. No celular com o app fechado, o Android pode não deixar religar o microfone sozinho ao conectar: abrir o app religa.
