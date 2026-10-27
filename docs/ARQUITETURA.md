# Arquitetura

```
 Carro (CAN) ──► ELM327 ──Bluetooth RFCOMM──► Elm327Client ──► ObdParser ──► Sample
                                                                               │
                              ObdSource (interface)                            ▼
                              ├─ BluetoothObdSource (real)              MonitorViewModel
                              └─ SimulatedSource (testes e demo)        ├─ FuelCalc (L/h, km/l)
                                                                        ├─ TripAccumulator (médias)
                                                                        ├─ Maintenance (óleo)
                                                                        └─ Store (SharedPreferences)
                                                                               │
                                                                               ▼
                                                                    Telas Compose (Painel, Consumo,
                                                                    Manutenção, Diagnóstico, Ajustes)
```

## Camadas

- **obd/**: protocolo. `Pid` define fórmulas, `ObdParser` interpreta respostas do ELM327, `Elm327Client` envia comandos AT e consultas.
- **data/**: fontes de dados (`ObdSource`) e persistência (`Store`).
- **domain/**: regras puras, sem Android: consumo, médias da viagem, óleo. É o que tem teste unitário.
- **ui/**: ViewModel e telas Compose.

## Ciclo de leitura

1. Conecta ao adaptador pareado (nome contém OBD, ELM ou LINK, ou nome escolhido nos Ajustes).
2. Inicializa: `ATZ`, `ATE0`, `ATL0`, `ATH0`, `ATS1`, `ATSP0`, e dispara a busca de protocolo com `0100`.
3. Descobre PIDs suportados (`0100`, `0120`, `0140`, `0160`).
4. Laço de leitura: PIDs rápidos (rotação, velocidade, MAF ou MAP) a cada ciclo; PIDs lentos (temperaturas, bateria, nível de combustível) a cada 10 ciclos.
5. Cada ciclo gera um `Sample` que alimenta o ViewModel.

## Estimativa de consumo

Ordem de preferência, usando o primeiro dado disponível:

1. **PID 5E** (taxa de combustível, L/h), se o carro responder.
2. **MAF** (g/s): `L/h = MAF × 3600 / (AFR × densidade)`.
3. **MAP + rotação + temperatura do ar** (densidade-velocidade): estima a vazão de ar pelo volume deslocado, pressão, temperatura e eficiência volumétrica; depois segue o mesmo cálculo do MAF.

`AFR` e densidade vêm da mistura de combustível configurada (fração de etanol equivalente: gasolina E30 = 0,30; etanol = 0,95). O resultado é multiplicado por um **fator de calibração**, ajustado a cada abastecimento de tanque cheio a tanque cheio:

```
fator = litros abastecidos / litros estimados desde o último tanque cheio
```

## Persistência

`Store` usa SharedPreferences com JSON: odômetro acumulado do app, dados da última troca de óleo, fração de etanol, fator de calibração, litros estimados desde o último tanque cheio e lista de abastecimentos. Sincronização na nuvem (Firebase) fica para a fase 3.
