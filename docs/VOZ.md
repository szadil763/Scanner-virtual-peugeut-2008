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

Números por extenso viram dígitos ("cento e vinte e cinco" = 125); "seis e onze" é o preço 6,11; "6 reais e 19 centavos" = 6,19. O preço é o número depois de "a", "por" ou antes de "o litro"; sem marca, o menor entre 1 e 20. Testes em `VoiceCommandsTest`.

## Como funciona no aparelho (`data/VoiceAssistant`)

- Reconhecimento de fala do próprio aparelho (`SpeechRecognizer`, pt-BR). Sem serviço para o app usar direto, tenta a tela de reconhecimento do sistema. Muitos multimídias chineses vêm sem o app Google: aí não há reconhecimento (alternativa futura: reconhecimento offline, ~50 MB).
- Resposta pelo leitor de voz do Android (`TextToSpeech`, pt-BR), como orientação de navegação: a música abaixa enquanto ouve e fala.
- Ajustes > Comando de voz > **Testar este aparelho**: mostra se há microfone permitido, reconhecimento de voz e voz em português.
- Permissão `RECORD_AUDIO`, pedida no primeiro toque; `<queries>` para enxergar os serviços de reconhecimento e de voz.
