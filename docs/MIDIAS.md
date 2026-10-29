# Mídias e Painel + Música

Pedido do dono (foto da 0.1.17): na tela dividida e no rodapé a música ficava espremida, com espaço sobrando no app.

## Limite do Android

Nenhum app consegue mostrar outro app (Spotify, Amazon Music) dentro da própria tela; só a tela dividida do sistema faz isso. Por isso o app **controla** a música dos outros apps em vez de embuti-los.

## Aba Mídias (`ui/MediaScreens.kt`)

- **Player grande**: capa, nome, artista, barra de tempo e botões de 68 a 96 dp. Usa a sessão de mídia do que estiver tocando (`data/MediaWatcher`, exige "acesso a notificações", o mesmo do mini player).
- **Fontes**: um botão por app de música instalado e por app que já tocou algo (assim entram o Bluetooth do celular e o rádio do multimídia, se ele publicar sessão de mídia). Um toque manda o app tocar em segundo plano (sessão existente ou tecla "tocar"); se em 3 s nada tocar, o app é aberto (o Spotify às vezes só toca depois de aberto).
- **Rádio pela internet**: toca no próprio app (`data/RadioService`, Media3/ExoPlayer, serviço de mídia em primeiro plano; aparece na notificação e nos botões do volante). Lista do catálogo aberto radio-browser.info (`data/RadioBrowser`, regras puras e testadas em `domain/Radio.kt`): favoritas, mais ouvidas do Brasil, da região (estado pelo GPS) e busca por nome, só com o carro parado. Com 2 ou mais favoritas, ⏮ e ⏭ trocam de rádio. `usesCleartextTraffic` está ligado porque muitas rádios transmitem em http.
- O foco de áudio do Android resolve a troca: ao tocar o rádio, o Spotify pausa, e vice-versa; a voz do GPS abaixa o som.

## Painel + Música

Chave na aba Mídias (fica gravada). O Painel passa a mostrar velocidade, consumo, rotação, temperatura, média e distância à esquerda e o player grande com as fontes à direita, sem usar a tela dividida. No rodapé ficam só Mapa e Música (sem o mini player repetido).

## O que fica para depois

- Navegar nas playlists do Spotify/Amazon dentro do app: esses apps só liberam a lista para clientes aprovados (Android Auto); o caminho oficial do Spotify exige cadastro no programa de desenvolvedores.
- Rádio FM nativo do multimídia: app fechado do fabricante. Se ele publicar sessão de mídia, aparece como fonte; senão, só abrindo o app dele.
