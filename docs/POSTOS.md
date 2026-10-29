# Postos e qualidade do combustível

Ao registrar um abastecimento (aba Consumo), o app guarda o **posto**: local pelo GPS do multimídia, endereço pela internet do celular (Geocoder do Android) e o nome, que o dono confere (ex.: "Shell Av. Brasil"). Postos a menos de 150 m são o mesmo. A localização fica só no aparelho.

## Nota do posto (`domain/FuelQuality.kt`)

Cada **abastecimento de tanque cheio** num posto abre um "tanque", que vai até o próximo abastecimento. Com as viagens gravadas nesse período (Saúde), o app mede:

| Sinal | Como | Pior que o normal |
|---|---|---|
| Litros reais ÷ previstos pelo ar | tanque cheio a cheio, sem parcial no meio (`Refuel.tankRatio`) | +3% (−1 ponto), +6% (−2); melhor que −3% (+1) |
| Avanço de ignição médio | viagens do tanque | −1,5° (−1), −3° (−2: octanagem baixa); +1,5° (+1) |
| Correção longa de combustível | viagens do tanque | +6% (−1), +10% (−2) |

"Normal" = mediana dos tanques **do mesmo combustível** (gasolina com gasolina, etanol com etanol), com pelo menos 3 tanques. Tanques com menos de 30 km rodados ficam de fora.

Nota do posto = média dos pontos dos seus tanques: ≥ +0,5 **bom**, ≤ −1,5 **suspeito (evitar)**, entre eles **normal**; sem comparação ainda, **poucos dados**.

A conta de litros reais ÷ previstos desconta trânsito e jeito de dirigir, porque o previsto vem do ar que o motor admitiu. Mesmo assim é estimativa: o app diz "rendeu pior que o normal no seu carro", nunca "posto adulterado".

## Aba Postos

Ranking com os bons e mais perto primeiro (suspeitos por último), distância, último preço, número de abastecimentos e o motivo da nota. **Ir** abre o app de mapa escolhido em Ajustes com o posto como destino (geo:, Maps e Waze); **Renomear** corrige o nome.

## Ideia para depois

A ANP publica em dados abertos o monitoramento de qualidade (PMQC) por posto; dá para cruzar com a nota quando houver internet.
