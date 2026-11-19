# Postagem no LinkedIn

## Como postar

1. No LinkedIn: **Começar publicação** → "+" → **Adicionar um documento** → envie `saida/carrossel.pdf`. Título do documento: `Eu e meu pai transformamos o multimídia do carro em um scanner`.
   - Post em documento vira carrossel de arrastar e costuma alcançar mais gente que imagem solta. Se preferir imagens, envie as 10 `saida/lamina-N.png` na ordem.
2. Cole o texto abaixo.
3. Se o seu pai tiver LinkedIn, marque ele no lugar de `@Adilson` (digite @ e escolha o perfil); se não tiver, troque por "meu pai".
4. Melhor horário: terça a quinta, entre 8h e 10h. Responda os comentários na primeira hora.

---

## Texto

O app dizia que o carro do meu pai fazia 6,2 km/l.
O painel do carro dizia 12,8.

Esse erro me ensinou mais do que muito curso. 👇

Eu e @Adilson, meu pai, transformamos o multimídia do Peugeot 2008 dele num scanner de diagnóstico. Um adaptador Bluetooth na tomada OBD2, embaixo do painel, e um app Android que conversa com a central do motor.

O carro responde em hexadecimal: "41 0C 1A F8" vira 1.726 rpm. Só que ele não tinha o sensor de ar, então calculamos o consumo pela física dos gases (pressão, rotação e temperatura). A conta estava certa. O motor real é que não era o do livro: o app gastava 2,5 vezes mais combustível no papel do que o carro na estrada. A solução foi calibrar com dado real, a cada tanque cheio.

Na primeira viagem, o app já achou uma falha guardada na central (com a luz da injeção apagada) e a mistura 31% pobre.

Mas a parte que mais me desenvolveu foi a que ninguém vê: persistência de dados. 💾

O carro desliga, a internet cai, e os dados não podem sumir. Então:
→ tudo é gravado primeiro no aparelho, separado por carro, e funciona offline
→ abastecimentos, histórico do tanque, viagens e milhares de leituras da bateria
→ meu celular e o do meu pai sincronizam pelo Firebase: o estado ao vivo a cada 30 s e o resto sempre que algo muda, com uma "impressão digital" dos dados para saber o que é novo
→ e quando os dois gravam ao mesmo tempo, vale a versão mais nova

Foi aí que apareceu o bug que mais me marcou: se um dado chegasse estragado da nuvem, o celular ficaria tentando ler para sempre e nunca mais enviaria nada. Nenhum teste pegou. Foi a leitura atenta do código.

O que eu levo desse projeto:
1️⃣ Dado salvo é uma promessa. Hoje penso primeiro no que acontece quando a internet cai, o aparelho desliga ou dois aparelhos gravam juntos.
2️⃣ O mundo real é o teste final. Fórmula certa com dado errado continua errada.
3️⃣ IA acelera, revisão protege. Fizemos tudo junto com o Claude Code, e aprendi que ler e questionar o código continua sendo trabalho nosso.
4️⃣ Paciência. Foram mais de 160 versões, muitas testadas no carro e voltando com foto e registro para a próxima.

E o melhor de tudo foi fazer isso junto com meu pai. ❤️

Qual carro a gente deve testar a seguir? Conta o modelo do seu aí embaixo 👇

#Android #Kotlin #Firebase #PersistenciaDeDados #OBD2 #InteligenciaArtificial #ClaudeCode #PaiEFilho
