# Dragon Sound

Aplicación Android de amplificación y escucha de audio en tiempo real. Usa el micrófono integrado del teléfono y reproduce por la salida seleccionada por Android.

> **Importante:** la app no mide dB SPL ni está certificada como dispositivo médico. El control de ganancia y el limitador digital no garantizan un nivel seguro para el oído. Empieza con el volumen bajo.

## Funciones

- Servicio de audio en primer plano y mosaico de Ajustes rápidos existentes.
- Filtro Voz con pasa-banda aproximado para priorizar habla (250–3400 Hz).
- Reducción de ruido mediante NoiseSuppressor de Android, activada al iniciar; la app indica si el dispositivo no la admite.
- Solicitud de reproducción LOW_LATENCY y búfer reducido al mínimo compatible. En Bluetooth, Android 12+ intenta usar la ruta de comunicación tipo llamada si está disponible; puede reducir el retardo, pero suele bajar la calidad de audio. Si no está disponible, se usa la ruta estándar. Android puede añadir retardo y no se garantiza latencia cero.
- Controles existentes de ganancia y ecualización. La escala 0–15x es ganancia digital, no dB SPL.
- APK universal con armeabi-v7a, arm64-v8a, x86 y x86_64.

## Seguridad

La compresión limita picos digitales, pero no mide ni limita la presión sonora real de cada combinación de teléfono y auriculares. Mantén el volumen bajo al iniciar y sube solo lo necesario. Si oyes silbido, distorsión o molestia, baja el volumen y detén la escucha.

## Compilar

Requisitos: Android SDK, NDK 25.1.8937393, CMake 3.22.1 y Java 17.

En Windows: gradlew.bat assembleDebug

En macOS/Linux: ./gradlew assembleDebug

El APK queda en app/build/outputs/apk/debug/app-debug.apk. El workflow de GitHub Actions también compila el APK universal.
