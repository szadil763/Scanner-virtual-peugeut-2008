# Roadmap

## Fase 0: preparação (feita)
- Identificar o multimídia (Android 13, Play Store, CANBOX genérico).
- Protótipo visual com dados simulados.
- Fórmulas OBD e de consumo validadas por script.
- Esqueleto Android com modo simulado.

## Fase 1: leitura real
- Comprar o ELM327 e rodar o teste (`TESTE-ADAPTADOR.md`).
- Compilar o app no Android Studio e corrigir erros.
- Conectar ao adaptador no celular, depois no multimídia.
- Confirmar PIDs e preencher `PIDS.md`.
- Escolher o método de consumo (5E, MAF ou MAP) conforme o carro.

## Fase 2: uso no dia a dia
- Calibrar consumo com 2 ou 3 abastecimentos de tanque cheio a tanque cheio.
- Alertas de troca de óleo e de temperatura alta.
- Leitura de falhas (modo 03) com tela de resultado.
- Ícone, nome e instalação do APK no multimídia.

## Fase 3: extras
- Sincronizar histórico com Firebase.
- Exportar abastecimentos em CSV.
- Gráficos de consumo por trecho e por mês.
- Custo por km com o preço do combustível.

## Ideias futuras
- Widget de painel compacto para a tela do multimídia.
- Aviso quando o consumo médio cair muito (pode indicar problema).

## Pendências levantadas após a escolha do adaptador

- Parar de consultar com o motor desligado (rotação 0 por alguns minutos) e reconectar ao ligar.
- Serviço em primeiro plano para o app continuar lendo o carro com outro app (ex.: Android Auto) na frente.
- Testar no multimídia: Bluetooth do adaptador junto com o telefone ligado ao rádio.

## Falhas (ver docs/FALHAS.md)

- Fase 1: leitura de códigos pendentes (modo 07), registro de falhas com foto dos dados ao vivo, gravidade por código.
- Fase 2: apagar falhas com confirmação dupla e bloqueio por gravidade, só depois do teste real no carro.
