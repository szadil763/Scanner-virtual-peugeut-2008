# Instalar o app no multimídia (APK de teste)

O GitHub compila o app sozinho a cada envio para a branch `main` e publica o instalador (APK) em **Releases**.

Link fixo do instalador:
`https://github.com/szadil763/monitor-peugeot-2008/releases/download/latest/monitor-2008.apk`

## No multimídia (Android 13)

1. Abra o navegador do multimídia e acesse o link acima (ou a página `.../releases/latest`).
2. Baixe `monitor-2008.apk`.
3. Abra o arquivo baixado. Se o Android avisar sobre "fontes desconhecidas", permita a instalação para o navegador usado.
4. Toque em Instalar e depois em Abrir.
5. Se o multimídia bloquear o download ou não tiver gerenciador de arquivos: baixe no celular e passe por pen drive, ou instale um gerenciador de arquivos pela Play Store.

Atualizar: baixe o APK novo e instale por cima. Os dados do app (abastecimentos, odômetro) ficam.

## Antes de abrir o app

1. Plugue o adaptador na porta OBD2 com a ignição ligada.
2. Pareie nas configurações de Bluetooth do multimídia (senha 1234, 8888 ou 0000).
3. No app: Ajustes, desligue "Dados simulados". Se o nome do adaptador não for reconhecido, digite o nome exato que aparece na lista de pareados.
4. Diagnóstico mostra a conexão, os dados que o carro declarou e o **registro do adaptador**. Toque em "Copiar registro" e cole numa conversa para análise.

## O que enviar depois do primeiro teste

- O registro do adaptador (botão Copiar registro).
- Se conectou: quais dados aparecem preenchidos no Painel (rotação, velocidade, MAF, MAP, nível de combustível).
- Se não conectou: a mensagem exata em Diagnóstico e a lista de dispositivos pareados que o app mostra.
