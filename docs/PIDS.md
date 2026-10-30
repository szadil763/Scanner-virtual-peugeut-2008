# PIDs usados (modo 01)

Resultado do primeiro teste no carro (28/10/2026, adaptador ELM327 v2.1 por BLE, protocolo A6 = CAN 11 bits 500 kbps). Responderam duas centrais: o motor e o câmbio automático (o câmbio declara só o PID 01). Códigos declarados, somando as duas: `01 03 04 05 06 07 0B 0C 0D 0E 0F 11 13 14 15 1C 20 21 40 47 56`.

| PID | Dado | Fórmula | Unidade | Carro responde? |
|---|---|---|---|---|
| 01 | Status: luz de injeção (bit 7 de A) e nº de falhas (A & 0x7F) | — | — | Sim (motor e câmbio; o app junta os dois) |
| 04 | Carga do motor | A × 100 / 255 | % | Sim |
| 05 | Temperatura do líquido de arrefecimento | A − 40 | °C | Sim |
| 06 | Correção curta de combustível (banco 1) | (A − 128) × 100 / 128 | % | Sim |
| 07 | Correção longa de combustível (banco 1) | (A − 128) × 100 / 128 | % | Sim |
| 0B | Pressão do coletor (MAP) | A | kPa | Sim |
| 0C | Rotação | (256 A + B) / 4 | rpm | Sim |
| 0D | Velocidade | A | km/h | Sim |
| 0E | Avanço de ignição | A / 2 − 64 | ° | Sim |
| 0F | Temperatura do ar de admissão | A − 40 | °C | Sim |
| 10 | Fluxo de ar (MAF) | (256 A + B) / 100 | g/s | **Não** |
| 11 | Posição do acelerador | A × 100 / 255 | % | Sim |
| 14 | Sonda lambda antes do catalisador | A / 200 | V | Sim |
| 15 | Sonda lambda depois do catalisador | A / 200 | V | Sim |
| 2F | Nível de combustível | A × 100 / 255 | % | **Não** |
| 31 | Distância desde que as falhas foram apagadas | 256 A + B | km | A confirmar (usado para achar km rodados sem o app, `domain/MissedDistance`) |
| 42 | Tensão do módulo (bateria) | (256 A + B) / 1000 | V | **Não** (app usa ATRV) |
| 46 | Temperatura ambiente | A − 40 | °C | **Não** |
| 5C | Temperatura do óleo | A − 40 | °C | Não |
| 5E | Taxa de combustível | (256 A + B) / 20 | L/h | Não |

A e B são o primeiro e o segundo byte de dados da resposta.

## Modo 03 (falhas armazenadas) e 07 (pendentes)

Em CAN (ISO 15765-4) a resposta é `43 NN` seguido de `NN` pares de bytes. Cada par vira um código: os 2 bits mais altos escolhem a letra (P, C, B, U), os 2 seguintes o primeiro dígito, e o restante forma 3 dígitos hexadecimais. Exemplo: `01 33` → `P0133`. O modo 07 (falhas pendentes, ainda sem acender a luz) tem o mesmo formato com prefixo `47`.

## Observações

- Nível e temperatura do óleo reais normalmente exigem protocolo da Peugeot, fora do OBD2 padrão.
- O 2008 não tem MAF (PID 10): o consumo sai pelo método MAP (pressão do coletor + rotação + ar de admissão). No teste deu 1,11 L/h em marcha lenta.
- O 2008 não informa nível de combustível (2F) nem tensão do módulo (42). A bateria vem do comando `ATRV` do adaptador (tensão no pino 16 da tomada OBD).
- Com o motor desligado e a ignição ligada, o MAP mostrou 92 kPa (pressão atmosférica do local).

## Leituras extras para testar no carro (a confirmar)

Anúncios de adaptador citam "percentual de etanol" e "quilometragem". Quem decide se existem é a central do carro, não o adaptador. Testar com o Car Scanner e anotar a resposta.

| PID | O que seria | Fórmula (SAE J1979) | Por que importa |
|---|---|---|---|
| 0x52 | Percentual de etanol no combustível | A × 100 / 255 | **Não declarado** pelo carro |
| 0xA6 | Hodômetro | (A·2²⁴ + B·2¹⁶ + C·2⁸ + D) / 10 km | **Não declarado** (a faixa 0x60 nem existe) |
| 0x2F | Nível de combustível | A × 100 / 255 | **Não declarado** |

Como nenhum respondeu, segue como está: mistura escolhida em Ajustes e km digitado.
