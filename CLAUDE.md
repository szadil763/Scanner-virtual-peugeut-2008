# CLAUDE.md

Contexto para continuar este projeto no Claude Code.

## O que é

App Android (Kotlin + Jetpack Compose) que lê o Peugeot 2008 2016 1.6 flex automático via adaptador ELM327 Bluetooth clássico e mostra consumo, velocidade, rotação, temperaturas, bateria e nível de combustível, mais controle de óleo e abastecimentos. Roda no multimídia Android 13 (1280x800) e em celulares.

Dono do projeto: Adilson. Idioma de UI, comentários e docs: português do Brasil.

## Comandos

```bash
node tools/verify-formulas.js        # confere fórmulas OBD e de consumo
cd android && gradle test            # testes unitários Kotlin (o Android Studio gera o ./gradlew ao importar)
cd android && gradle assembleDebug   # gera o APK de teste
```

O protótipo em `prototype/index.html` não tem build: basta abrir no navegador.

## Regras

1. **Somente leitura.** Só modos OBD 01 (dados atuais) e 03 (falhas armazenadas). Nunca enviar escrita, UDS, codificação nem modo 04 (apagar falhas) sem tela de confirmação pedida pelo dono. O desenho do botão de apagar, com gravidade por código, está em `docs/FALHAS.md` (fase 2, depois do teste real).
2. **Bluetooth clássico (RFCOMM/SPP, UUID 00001101-0000-1000-8000-00805F9B34FB) e BLE.** O adaptador do dono é duplo: aparece como `OBDII` (clássico) e `OBDBLE` (BLE). O pareamento clássico falhou no multimídia ("PIN não confere" com 1234, 0000, 6789 e 8888, sem pedir o PIN), então o app tenta o clássico pareado e, se não houver, procura o adaptador por BLE (`data/BleLink.kt`), que não usa PIN.
3. **Não inventar suporte a PID.** Use a consulta 0100/0120/0140/0160 e só leia o que o carro declarar. A tabela em `docs/PIDS.md` marca o que está confirmado e o que está a confirmar.
4. **Paridade de fórmulas.** Qualquer mudança em `obd/Pid.kt`, `obd/ObdParser.kt` ou `domain/FuelCalc.kt` deve ser refletida em `tools/verify-formulas.js` e `prototype/index.html`, com os mesmos números de teste.
5. **UI para multimídia:** alvos de toque grandes (mínimo 56 dp), fonte grande, alto contraste, sem animação que distraia. O app não pede digitação enquanto a velocidade é maior que 0.
6. O CANBOX Hiworld do multimídia não é fonte de dados (sem API aberta).
7. **Carro desligado = sem consulta.** Se a rotação ficar em 0 por alguns minutos (ou a conexão cair), parar de consultar o adaptador e desconectar; reconectar sozinho quando o motor ligar. Consultar com a ignição desligada mantém os módulos acordados e descarrega a bateria. O adaptador mini não dorme: recomendar tirá-lo se o carro ficar parado mais de ~7 dias. (Implementado em `BluetoothObdSource`, ainda não validado no carro: pausa de 60 s sem resposta de rotação, desliga após 5 min parado.)

## Decisões em aberto

- Quais PIDs o carro responde (MAF? MAP? 5E? 2F?). Só se sabe após o teste com o adaptador.
- Teor de etanol da gasolina comum: o padrão do app é 30% (E30). O governo aprovou aumento para 32%, então o valor é configurável.
- Eficiência volumétrica do motor (padrão 0,80) é chute inicial; ajustar com tanque cheio a tanque cheio.
- Intervalo de troca de óleo (padrão 10.000 km ou 12 meses): confirmar no manual do carro.
- Firebase para sincronizar histórico: opcional, fase 3 do roadmap.

## Estado

App compilado pelo GitHub Actions (`.github/workflows/build-apk.yml`, roda em `main` e na branch de desenvolvimento) e **testado no carro em 28/10/2026 (versão 0.1.6)**: conecta pelo BLE (`OBDBLE`) e o painel mostra rotação, velocidade, temperaturas e consumo pelo método MAP. O que o carro responde está em `docs/PIDS.md`: sem MAF, sem nível de combustível, sem PID 42 (a bateria vem do `ATRV`). Motor e câmbio respondem juntos às consultas do modo 01, por isso o parser junta as respostas de todas as centrais.

O APK sai em Releases (`latest`); a versão (0.1.N, N = número do build) aparece na tela Diagnóstico. Ver `docs/INSTALACAO.md`.

Pendências conhecidas: serviço em primeiro plano (a leitura pode pausar com outro app na frente), leitura de códigos pendentes (modo 07), registro de falhas com foto dos dados, ícone do app.
