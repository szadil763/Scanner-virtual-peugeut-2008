# Falhas (DTC): monitorar e, no futuro, apagar com critério

Origem: o dono já teve uma falha no sensor MAP que sumiu sozinha. Falha intermitente é a mais difícil de investigar, porque quando chega na oficina ela já foi apagada.

## Fase 1: monitorar (somente leitura)

- Ler códigos confirmados (modo 03). Avaliar também os pendentes (modo 07, só leitura): costumam mostrar o problema antes da luz acender.
- Registro de falhas: quando aparecer um código novo ou a luz da injeção (MIL) acender, salvar data/hora e uma foto dos dados ao vivo (rotação, MAP, carga, temperaturas, bateria, velocidade). Isso é o que falta para explicar uma falha intermitente.
- Mostrar cada código com: o que costuma significar, gravidade (abaixo) e o que fazer.

## Fase 2: apagar, só depois do teste real no carro

- Botão dentro de Diagnóstico, só com velocidade 0.
- Tela de confirmação dupla com o código, a gravidade e o aviso do que se perde: dados do momento da falha e testes internos da central.
- Nunca apagar sozinho. Registrar no histórico o que foi apagado e quando.
- Bloquear (ou exigir confirmação reforçada) quando a gravidade for vermelha.
- A luz volta se o defeito continua, e códigos permanentes não saem com o comando de apagar.

## Gravidade (orientação genérica, não é diagnóstico)

O mesmo código pode ter causas diferentes. A tabela só decide o nível de cuidado do app.

| Nível | Famílias de código | Orientação |
|---|---|---|
| Vermelho: não apagar, procurar oficina | P0300–P0312 falha de ignição (luz piscando = parar o carro); P0100–P0109 MAF/MAP; P0120–P0129 borboleta/pedal; P0190–P0199 pressão de combustível; P0200–P0299 injetores; P0335–P0349 rotação/comando; P0560 tensão; P06xx módulo; P07xx câmbio; U comunicação | Pode afetar dirigibilidade, consumo ou danificar peças. Apagar apaga a pista. |
| Amarelo: investigar antes, apagar só se corrigiu | P0130–P0167 sonda lambda; P0170–P0175 mistura rica/pobre; P0420 catalisador; EGR; P0500 velocidade | Observar se volta. Se voltar, oficina. |
| Verde: costuma ser apagável depois de corrigir | P0440–P0457 evaporação (tampa do tanque mal fechada); P0110–P0114 temperatura do ar | Corrigir a causa (ex.: fechar bem a tampa), apagar e observar. |

Códigos de freio, airbag e carroceria (C e B) normalmente não aparecem no modo 03 e ficam fora do app.

## Falha intermitente de MAP

O app usa o MAP quando o carro não entrega o fluxo de ar (MAF), para estimar o consumo. Se o MAP falhar, a estimativa de consumo fica errada naquele momento. O app deve avisar quando a leitura do MAP parecer incoerente (valor travado ou saltando) e registrar o evento.
