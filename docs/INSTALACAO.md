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

## Dois APKs: multimídia e celular

Desde a 0.1.32 cada versão sai em dois arquivos na página de Releases:

| Arquivo | Para | Diferença |
|---|---|---|
| `monitor-2008.apk` | Multimídia do carro | Completo: inclui o acesso a notificações (ver e controlar a música dos outros apps) |
| `monitor-2008-celular.apk` | Celular | Sem o acesso a notificações; a aba Mídias fica com o rádio e os botões que abrem os apps de música |

Motivo: no celular, o Google Play Protect bloqueia a instalação de fora da loja de apps que pedem acesso a notificações ("App blocked to protect your device", foto do dono na 0.1.31). Os dois APKs são o mesmo app (`productFlavors` `multimidia` e `celular` em `android/app/build.gradle.kts`; o serviço do mini player fica só em `src/multimidia/AndroidManifest.xml`). O botão de atualização baixa sempre o APK do mesmo tipo (`data/Features.apkName`).

Se o celular mesmo assim bloquear: Play Store > foto do perfil > Play Protect > engrenagem > desligar "Verificar apps com o Play Protect", instalar e ligar de novo.
