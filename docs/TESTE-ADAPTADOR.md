# Teste do adaptador ELM327

Objetivo: descobrir quais dados o seu Peugeot 2008 realmente entrega, antes de depender deles no app.

## Antes de começar

- Faça o teste com o carro **parado**, de preferência com alguém ajudando na hora de ver o carro em movimento.
- O adaptador mini fica plugado na porta OBD2, normalmente embaixo do painel, perto da coluna de direção.
- Se o carro ficar parado por vários dias, desplugue o adaptador, porque ele consome um pouco da bateria.

## Passo a passo

1. Ligue o carro (ou só a ignição) e plugue o adaptador. A luz dele deve acender.
2. No multimídia: Configurações > Bluetooth > procure o adaptador (nomes comuns: OBDII, V-LINK, ELM327). Senha comum: `1234` ou `0000`.
3. Instale o **Car Scanner ELM OBD2** pela Play Store do multimídia.
4. No app: Configurações > Conexão > Bluetooth, escolha o adaptador. Perfil do carro: genérico OBD2, protocolo automático.
5. Conecte. Se falhar, desligue e ligue o carro, e tente de novo.
6. Abra a lista de **sensores / PIDs suportados** e anote o que aparece.

## O que anotar

Para cada item abaixo, marque se aparece e se tem valor (não só "N/A"):

| Dado | Aparece? | Valor com carro ligado parado |
|---|---|---|
| Rotação | | |
| Velocidade | | |
| Temperatura do motor | | |
| Fluxo de ar (MAF) | | |
| Pressão do coletor (MAP) | | |
| Temperatura do ar de admissão | | |
| Carga do motor | | |
| Posição do acelerador | | |
| Nível de combustível | | |
| Tensão da bateria / do módulo | | |
| Taxa de combustível (L/h) | | |
| Correções de combustível | | |
| Temperatura do óleo | | |

Tire também uma foto da lista de PIDs suportados.

## Se algo der errado

- **Não conecta:** confira se o Bluetooth do multimídia está ligado e se o adaptador aparece pareado. Clones baratos às vezes precisam de dois ou três tentativas.
- **Conecta e os valores ficam em branco:** teste outro protocolo (ISO 15765-4 CAN 11 bits 500 kbps é o mais provável).
- **O multimídia reinicia ou o carro apresenta falha:** desplugue o adaptador e não use mais esse exemplar. Clones de baixa qualidade podem causar isso.

## Depois do teste

Me mande as fotos ou a tabela preenchida. Com isso eu ajusto a coluna "Carro responde?" em `PIDS.md` e escolho o método de consumo (5E, MAF ou MAP).
