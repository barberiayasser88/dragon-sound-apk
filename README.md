

## Notificaciones de versiones

La versión 1.0.1 incorpora Firebase Cloud Messaging y se suscribe al tema `dragon-sound-updates`. La versión 1.0.0 no incluye FCM, por lo que habrá que instalar manualmente la 1.0.1 antes de recibir avisos automáticos de futuras versiones. Al tocar un aviso, se abre la página del release en GitHub.

GitHub Actions envía avisos solo cuando se publica un release estable. Para compilar y enviar notificaciones, configura estos secretos en **Settings → Secrets and variables → Actions**:

- `FIREBASE_ANDROID_CONFIG_B64`: contenido de `google-services.json` de Firebase, codificado en Base64.
- `FIREBASE_SERVICE_ACCOUNT_JSON`: JSON de una cuenta de servicio con permiso para enviar mensajes de Firebase Cloud Messaging.

No guardes el JSON de la cuenta de servicio ni otros valores secretos en el repositorio.
