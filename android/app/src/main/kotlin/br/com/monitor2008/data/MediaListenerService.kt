package br.com.monitor2008.data

import android.service.notification.NotificationListenerService

/**
 * Existe só para o Android liberar a leitura do que está tocando (Spotify, YouTube Music...).
 * O usuário ativa em "acesso a notificações"; o app não lê nem guarda o conteúdo das notificações.
 */
class MediaListenerService : NotificationListenerService()
