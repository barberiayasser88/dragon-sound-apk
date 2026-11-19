# Guía de usuario — Dragon Sound

Dragon Sound amplifica digitalmente el audio captado por el micrófono integrado y lo reproduce por la salida seleccionada por Android. No sustituye una evaluación ni un audífono calibrado.

> **Seguridad:** la aplicación no mide dB SPL y no está certificada como dispositivo médico. La ganancia 0–15x es una escala digital; la compresión reduce picos digitales, pero no garantiza un nivel seguro en los auriculares. Comienza con el volumen del teléfono bajo y sube solo lo necesario.

## Uso básico

1. Conecta tus auriculares con cable o Bluetooth antes de activar la escucha.
2. Mantén bajo el volumen del teléfono y toca **ACTIVAR**.
3. En **FILTRO DE SONIDO**, elige **Voz (Pasa-banda)** para priorizar el habla.
4. Ajusta la ganancia gradualmente. No hay equivalencia fija entre el valor mostrado y dB SPL.
5. Si oyes silbido, distorsión o molestia, baja el volumen y toca **DETENER**.

La app mantiene la escucha mediante un servicio en primer plano y conserva el mosaico de Ajustes rápidos **Oído Digital**. La disponibilidad del mosaico y los controles en pantalla bloqueada depende de Android y del fabricante.

## Reducción de ruido

El control **Reducción de ruido** activa el efecto NoiseSuppressor proporcionado por Android cuando el dispositivo lo admite. Está activado por defecto. Al iniciar, el botón indica si el efecto quedó **ACTIVO**; si Android no lo ofrece o no permite activarlo, mostrará **NO DISPONIBLE**. En ese caso no se simula la reducción: puedes desactivar el control y seguir usando el audio.

## Retardo y auriculares

La app solicita una ruta de reproducción de baja latencia y usa el búfer mínimo compatible con el dispositivo. Aun así, Android, el hardware y en particular Bluetooth A2DP pueden añadir retardo. No se puede garantizar retardo cero. Si el sonido retardado de tu propia voz te confunde, detén la escucha mientras hablas o prueba una salida cableada; la percepción final debe comprobarse en el teléfono y los auriculares concretos.

## Filtros y ecualización

- **Voz:** pasa-banda aproximado de 250–3400 Hz, pensado para resaltar parte de la banda del habla; no elimina por sí solo todo el ruido.
- **Normal:** sin filtro de voz.
- **Grave / Agudo:** controles de ecualización existentes; úsalos con moderación.

La reducción de ruido es independiente del filtro Voz. La calidad y el nivel final dependen del micrófono, la salida, los auriculares y sus propios ajustes.

## Si no se escucha

- Comprueba que Dragon Sound tenga permiso de micrófono y que el volumen de salida esté bajo pero audible.
- Comprueba la salida activa en Android y vuelve a conectar los auriculares.
- Si el botón dice **NO DISPONIBLE**, ese dispositivo no confirmó el efecto de reducción de ruido.
- Si aparece silbido o acople, baja el volumen, aléjate del altavoz y detén la escucha si persiste.
- Bluetooth puede sonar más tarde que los auriculares con cable; esto es propio de la ruta del dispositivo y no desaparece por completo con un búfer pequeño.

## Instalación y compilación

El APK universal de GitHub Actions incluye armeabi-v7a, arm64-v8a, x86 y x86_64. Para compilar con Android SDK, NDK 25.1.8937393, CMake 3.22.1 y Java 17, usa gradlew.bat assembleDebug en Windows o ./gradlew assembleDebug en macOS/Linux.
