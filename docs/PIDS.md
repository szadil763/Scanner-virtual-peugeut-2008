# PIDs usados (modo 01)

"A confirmar" significa que só o teste com o adaptador mostra se o carro responde. Preencha a coluna **Carro responde?** depois do teste (`TESTE-ADAPTADOR.md`).

| PID | Dado | Fórmula | Unidade | Carro responde? |
|---|---|---|---|---|
| 04 | Carga do motor | A × 100 / 255 | % | A confirmar |
| 05 | Temperatura do líquido de arrefecimento | A − 40 | °C | A confirmar |
| 06 | Correção curta de combustível (banco 1) | (A − 128) × 100 / 128 | % | A confirmar |
| 07 | Correção longa de combustível (banco 1) | (A − 128) × 100 / 128 | % | A confirmar |
| 0B | Pressão do coletor (MAP) | A | kPa | A confirmar |
| 0C | Rotação | (256 A + B) / 4 | rpm | A confirmar |
| 0D | Velocidade | A | km/h | A confirmar |
| 0F | Temperatura do ar de admissão | A − 40 | °C | A confirmar |
| 10 | Fluxo de ar (MAF) | (256 A + B) / 100 | g/s | A confirmar |
| 11 | Posição do acelerador | A × 100 / 255 | % | A confirmar |
| 2F | Nível de combustível | A × 100 / 255 | % | A confirmar |
| 42 | Tensão do módulo (bateria) | (256 A + B) / 1000 | V | A confirmar |
| 46 | Temperatura ambiente | A − 40 | °C | A confirmar |
| 5C | Temperatura do óleo | A − 40 | °C | Improvável |
| 5E | Taxa de combustível | (256 A + B) / 20 | L/h | Improvável |

A e B são o primeiro e o segundo byte de dados da resposta.

## Modo 03 (falhas armazenadas)

Em CAN (ISO 15765-4) a resposta é `43 NN` seguido de `NN` pares de bytes. Cada par vira um código: os 2 bits mais altos escolhem a letra (P, C, B, U), os 2 seguintes o primeiro dígito, e o restante forma 3 dígitos hexadecimais. Exemplo: `01 33` → `P0133`.

## Observações

- Nível e temperatura do óleo reais normalmente exigem protocolo da Peugeot, fora do OBD2 padrão.
- Muitos carros flex dessa geração usam sensor MAP em vez de MAF. Se o PID 10 não for suportado, o app usa o método MAP.

## Leituras extras para testar no carro (a confirmar)

Anúncios de adaptador citam "percentual de etanol" e "quilometragem". Quem decide se existem é a central do carro, não o adaptador. Testar com o Car Scanner e anotar a resposta.

| PID | O que seria | Fórmula (SAE J1979) | Por que importa |
|---|---|---|---|
| 0x52 | Percentual de etanol no combustível | A × 100 / 255 | Daria a mistura real; hoje o app usa E30 fixo ou o que você escolher |
| 0xA6 | Hodômetro | (A·2²⁴ + B·2¹⁶ + C·2⁸ + D) / 10 km | Alimentaria o controle de óleo sem digitar km; raro em carros de 2016 |
| 0x2F | Nível de combustível | A × 100 / 255 | Já previsto |

Se 0x52 ou 0xA6 responderem "NO DATA", seguir como está (mistura escolhida em Ajustes, km digitado).
