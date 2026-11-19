# Postagem no LinkedIn

## Como postar

1. No LinkedIn: **Começar publicação** → ícone de **documento** (o "+" → "Adicionar um documento") → envie `saida/carrossel.pdf`. Título do documento: `Transformei o multimídia do carro em um scanner`.
   - Post em documento vira carrossel de arrastar e costuma alcançar mais gente que imagem solta. Se preferir imagens, envie as 8 `saida/lamina-N.png` na ordem.
2. Cole o texto abaixo.
3. Logo depois de publicar, comente o link do repositório (o LinkedIn entrega menos posts com link no texto):
   `https://github.com/szadil763/Scanner-virtual-peugeut-2008`
4. Melhor horário: terça a quinta, entre 8h e 10h. Responda os comentários na primeira hora.

---

## Texto

O carro do meu pai tinha um problema que o painel não mostrava.

Eu descobri pelo multimídia dele. Que eu transformei num scanner de diagnóstico. 🚗

Todo carro tem, embaixo do painel, uma tomada chamada OBD2. Por ela a central do motor conta tudo: rotação, temperatura, ar, consumo, falhas guardadas.

Com um adaptador Bluetooth barato, qualquer Android consegue ler. Mas os apps prontos são confusos e nada feitos para olhar dirigindo.

Então eu fiz o meu.

Na primeira viagem com o app, ele achou duas coisas no Peugeot 2008 do meu pai:
→ uma falha guardada na central, com a luz da injeção apagada
→ a mistura 31% pobre, a central compensando falta de combustível

Hoje o app:
✅ mostra o consumo e quanto cada viagem custou em reais
✅ explica as falhas em português e monta o relatório para o mecânico
✅ avalia a saúde do motor a cada viagem
✅ compara os postos: o próprio motor mostra qual combustível rende menos
✅ avisa troca de óleo, bateria fraca e motor quente, falando

Roda no multimídia e no celular, em 3 carros da família, e nunca escreve nada na central: só lê.

E a parte que mais me ensinou: construí tudo com IA (Claude Code).

A IA escreveu boa parte do código. Mas quem dizia se estava certo era o carro. Cada versão ia para o multimídia e voltava com foto e registro. Mais de 160 versões depois, aprendi que:
1️⃣ regra clara vale mais que prompt bonito ("só leitura", "não inventar sensor", "nada de digitar dirigindo")
2️⃣ teste no mundo real ganha de qualquer teoria
3️⃣ revisar continua sendo trabalho de gente: um PR inteiro parecia certo e escondia um bug que travaria a sincronização

O código é aberto (link nos comentários).

Qual carro eu devo testar a seguir? Conta o modelo do seu aí embaixo 👇

#Android #Kotlin #OBD2 #InteligenciaArtificial #ClaudeCode #Carros #Desenvolvimento

---

## Primeiro comentário

O código está aqui, aberto: https://github.com/szadil763/Scanner-virtual-peugeut-2008

Os APKs para instalar estão em Releases. Precisa só de um adaptador ELM327 Bluetooth na tomada OBD2.
