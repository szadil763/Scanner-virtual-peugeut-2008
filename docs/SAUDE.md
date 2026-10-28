# Tela Saúde (manutenção preventiva)

Indicadores que costumam mudar **antes** de uma falha. Não é diagnóstico: quando algo fica amarelo ou vermelho, o app explica o que costuma ser e o que verificar.

## Como funciona

- A cada viagem com o adaptador real, o app grava um **resumo** (`domain/Health.kt`, `HealthRecorder`) em `viagens.json` no armazenamento do app (últimas 400 viagens). Simulação não grava.
- A viagem termina quando o motor fica parado por 30 s, a conexão cai ou o app fecha. Viagens com menos de 2 min de motor ligado são descartadas.
- Falhas armazenadas (modo 03) e pendentes (modo 07) são lidas uma vez por viagem, logo que o motor liga.
- `HealthRules` avalia as **últimas 3 viagens** (média) contra limites fixos. Borboleta e vácuo comparam com o **normal do carro**: a mediana das primeiras 5 viagens; antes disso aparecem como "aprendendo".

Definições: **marcha lenta** = parado, motor ligado e quente (≥ 80 °C). **Quente** = arrefecimento ≥ 70 °C (injeção em malha fechada).

## Indicadores e limites

| Item | Dado | Atenção | Alerta |
|---|---|---|---|
| Luz de injeção e falhas | PID 01, modos 03 e 07 | falha pendente | luz acesa ou falha armazenada |
| Mistura | correção longa (07); correção total em marcha lenta (06 + 07) | \|LTFT\| ≥ 10% | \|LTFT\| ≥ 20% |
| Sonda lambda | faixa de oscilação da sonda 1 (14) | < 0,5 V | < 0,3 V |
| Catalisador | oscilação da sonda 2 (15) ÷ sonda 1 | > 60% | > 80% |
| Marcha lenta | desvio da rotação; média | desvio ≥ 60 rpm ou média fora de 600–1000 | desvio ≥ 120 rpm |
| Corpo de borboleta | abertura em marcha lenta (11) vs normal | +2 pontos | +4 pontos |
| Vácuo do motor | MAP em marcha lenta (0B) vs normal | +5 kPa | +10 kPa |
| Arrefecimento | temperatura máxima; tempo até 80 °C | ≥ 100 °C, aquecimento > 20 min, ou < 75 °C após 20 min rodando | ≥ 105 °C |
| Bateria e alternador | ATRV em repouso e com motor ligado | repouso < 12,2 V; ligado fora de 13,4–14,8 V | repouso < 11,8 V; ligado fora de 13,0–15,0 V |
| Avanço de ignição | média com motor quente (0E) | só acompanhamento | — |

Carro flex: logo depois de trocar de combustível, a correção muda por uns 50 km enquanto a central se adapta.

## Botão "Gerar dicas de manutenção"

`domain/Advisor.kt` junta viagens, indicadores, falhas (com explicação dos códigos mais comuns), itens por km/data, padrão de uso (viagens curtas = uso severo; trânsito parado) e consumo do trajeto, e gera dicas priorizadas (alta, atenção, dica) com observações do 2008 1.6 EC5 flex automático: bobinas individuais, carcaça plástica da válvula termostática, respiro do cárter, coxins, fluido do câmbio automático de 4 marchas (família AL4) e correia dentada com tensor e bomba d'água. Regras fixas, sem internet; intervalos exatos sempre com "confirme no manual".

## Limitações

- A sonda é lida a ~2–3 vezes por segundo pelo BLE, então os "cruzamentos por minuto" são aproximados; o critério de saúde usa a faixa de oscilação.
- Pela tomada OBD do 2008 não há nível/pressão de óleo, temperatura do câmbio, pastilhas nem pneus.

## Próxima etapa (3)

Previsões por tendência ("no ritmo atual, passa do limite em ~X km"), depois de 2–3 semanas de viagens gravadas.
