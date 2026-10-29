# Vários carros

O app foi feito no Peugeot 2008 e passa a atender outros modelos por **perfil** (`domain/Vehicle.kt`, testes em `VehicleTest`).

## O que muda por carro

- **Cilindrada** (consumo pelo método MAP, quando o carro não informa MAF nem taxa de combustível).
- **Limite de consumo alto** na cidade (gasolina e etanol), usado nas dicas.
- **O que verificar primeiro** quando cada indicador da Saúde sai do normal (defeitos conhecidos do motor).
- **Dicas fixas** do câmbio e das verificações a cada revisão.
- Nome do carro no relatório para o mecânico e nos textos.

O que o carro responde pela tomada OBD **não** fica no perfil: em qualquer modelo o app pergunta ao carro (PIDs suportados) e só lê o que ele declarar. Por isso o mesmo app serve em outros carros flex.

## Perfis

| Carro | Motor | Câmbio | Cilindrada | Consumo alto abaixo de (gasolina / etanol) |
|---|---|---|---|---|
| Peugeot 2008 | 1.6 16V flex (EC5) | automático 4 marchas (AL4) | 1,598 L | 8,0 / 5,5 km/l |
| Citroën C4 Lounge | 2.0 16V flex (EW10) | automático (4 ou 6 marchas, conforme o ano) | 1,997 L | 7,0 / 5,0 km/l |

## Instalar em outro carro

1. Cada carro com o próprio adaptador (OBDBLE) e o app instalado no próprio multimídia: os dados (viagens, Saúde, abastecimentos, postos) ficam separados.
2. Na primeira abertura o app pergunta qual é o carro (dá para mudar em Ajustes). Instalação antiga que já rodou é considerada 2008.
3. Primeiro teste: aba Diagnóstico, "Dados que o carro declarou suportar". Mandar foto: diz se o carro tem MAF (consumo mais preciso), nível de combustível, PID 42 (bateria) etc.
4. Calibrar o consumo com dois tanques cheios seguidos, como no 2008. A eficiência volumétrica (Ajustes) começa em 0,80.

## Acrescentar um modelo

Basta um novo `Vehicle` em `Vehicles.all` com motor, cilindrada, câmbio, limites de consumo e as notas por indicador (todas as chaves de `VehicleTest.everyProfileHasNotesForTheModelSpecificItems`). Para o carro do irmão e do cunhado do dono: informar marca, modelo, ano, motor e câmbio.
