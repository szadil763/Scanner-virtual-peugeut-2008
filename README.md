# Monitor Peugeot 2008

App Android que lê o carro por um adaptador **OBD2 ELM327 Bluetooth** e mostra, em tela grande, consumo (km/l), velocidade, rotação, temperatura do motor, bateria e nível de combustível. Também controla a **troca de óleo por km e data** e o histórico de **abastecimentos**.

Alvo principal: o multimídia Android 13 do carro (tela 1280x800, Play Store). Também roda em celular Android.

- Carro: Peugeot 2008 2016, 1.6 flex, câmbio automático
- Multimídia: Android 13, 4 GB RAM, 64 GB, Bluetooth 5.0
- Adaptador: ELM327 mini (Bluetooth clássico, não BLE)

## Estado atual

| Parte | Estado |
|---|---|
| Protótipo do painel (`prototype/index.html`) | Pronto, com dados simulados |
| Fórmulas OBD e de consumo (`tools/verify-formulas.js`) | Validadas por script de teste |
| App Android (`android/`) | Esqueleto completo, **ainda não compilado** (sem Android Studio neste ambiente) |
| Teste real com o carro | Pendente: depende do adaptador (`docs/TESTE-ADAPTADOR.md`) |

## Estrutura

```
prototype/        Protótipo HTML 1280x800 (abra no navegador)
android/          Projeto Android (Kotlin + Jetpack Compose)
tools/            Script Node que confere as fórmulas
docs/             Arquitetura, tabela de PIDs, teste do adaptador, roadmap
CLAUDE.md         Contexto para o Claude Code continuar o projeto
```

## Como ver o protótipo

Abra `prototype/index.html` no navegador. Na janela do desenvolvedor, use tela de 1280x800 para ver como ficará no multimídia.

## Como abrir o app Android

1. Instale o Android Studio.
2. Abra a pasta `android/`.
3. O Android Studio gera o Gradle Wrapper e sincroniza as dependências.
4. Rode no celular ou emulador. O app inicia em **modo simulado**, sem precisar do adaptador.
5. Testes unitários: `./gradlew test`.

## Como testar no carro

Siga `docs/TESTE-ADAPTADOR.md`. Resumo: compre o adaptador, instale o Car Scanner no multimídia, descubra quais PIDs o carro responde e anote. O app só usa o que o carro realmente entregar.

## Limitações conhecidas

- O consumo é **estimado** a partir do fluxo de ar (MAF), da pressão do coletor (MAP) ou da taxa de combustível (PID 5E), conforme o que o carro responder. O fator de calibração se ajusta com abastecimentos de tanque cheio a tanque cheio.
- Em carro flex a mistura real no tanque muda o resultado; o app pede o tipo de combustível a cada abastecimento.
- Nível e temperatura do óleo normalmente **não** saem pelo OBD2 padrão. O app controla a troca por km e data.
- O CANBOX Hiworld do multimídia não é usado: não há API aberta para apps de terceiros.
- Uso somente leitura. O app não escreve nada na central do carro.
- Configure o app com o carro parado. Dirigindo, só olhe o painel.

## Instalar no multimídia

O GitHub compila o app e publica o APK em Releases. Passo a passo em [`docs/INSTALACAO.md`](docs/INSTALACAO.md).
