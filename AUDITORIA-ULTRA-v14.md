# Auditoría de compatibilidad y rendimiento — V14 Ultra

Fecha: 2026-11-23. El árbol original de V14 se revisó antes de los cambios. La edición normal conserva su workflow; la variante se construye de forma independiente en `.github/workflows/build-ultra.yml`.

## Flujo original de compilación/exportación

`.github/workflows/build.yml` se ejecuta en `main`, en la etiqueta `v14` o manualmente; usa JDK 17 y selecciona la versión más nueva de Android Build Tools y `android.jar` disponible en el runner. Primero genera un icono, compila y enlaza una plantilla de juego, compila Java 8 y convierte clases a DEX; luego repite el proceso para el editor, incluye `apksig`, integra `template.apk`, alinea y firma el APK y publica un ZIP como artefacto. El flujo original fija plantilla `minSdk 24 / targetSdk 28` y editor `minSdk 26 / targetSdk 33`; D8 recibía `--min-api 24` y `26`, respectivamente.

La variante usa el mismo proceso y fuentes V14, pero crea un APK/ZIP distintos: plantilla `minSdk 21 / targetSdk 28`, editor `minSdk 21 / targetSdk 33`, y D8 `--min-api 21` en ambos. El target del editor permanece en 33; el 28 de la plantilla exportada conserva la política de V14. El workflow comprueba badging, alineación, firma y la integridad ZIP antes de subir el artefacto.

## Revisión de APIs

- `VideoLayer.java`: `MediaPlayer.seekTo(long, int)` requiere API 26 y ya está tras `SDK_INT >= 26`; en API 21–25 se usa `seekTo(int)`.
- `Exporter.java`: los formatos enum `WEBP_LOSSLESS` y `WEBP_LOSSY` son API 30. El acceso se cambió a `valueOf` bajo comprobación de API, con `WEBP` como fallback, para evitar referencias estáticas a constantes ausentes en Android 5–8.1.
- `PlayActivity.java`: `finishAndRemoveTask()` está disponible desde API 21, justo en el límite solicitado.
- `GameView.java`: `postInvalidateOnAnimation()` (API 16), `TextureView` (API 14), `StandardCharsets` (API 19) y el constructor antiguo de `StaticLayout` no elevan el mínimo por encima de 21.
- Hay lambdas y métodos `default` de interfaces Java 8; D8 debe desugaring con `--min-api 21`. Aapt2, D8 y compilación están fijados explícitamente a ese mínimo para la variante.
- El manifiesto editor declara `android:exported`, atributo que se necesita al compilar para plataformas recientes; debe comprobarse en runtime API 21, ya que no se dispone aquí de esos dispositivos.
- La compatibilidad del `apksig` incluido con las bibliotecas de plataforma de API 21 no se puede certificar por inspección solamente; la ejecución de la exportación real en API 21 queda pendiente de la prueba en dispositivo.

## Cuellos de botella verificables en el código (no son mediciones de dispositivo)

- La exportación original materializaba el APK plantilla completo en memoria; luego copiaba cada entrada ZIP a otro `byte[]`. Para imágenes mantenía a la vez los bytes originales, el bitmap decodificado y un `ByteArrayOutputStream` de la conversión. Es un pico de memoria proporcional al tamaño de la plantilla/asset más sus representaciones y trabajo evitable.
- `Game.img()` conserva bitmaps en un `HashMap` sin límite de bytes visible en el código. La carga decodifica el recurso original; un proyecto grande o bitmaps de gran resolución puede ejercer presión de memoria en móviles API 21 de poca RAM.
- `GameView.onDraw()` actualiza y dibuja mientras `Game.active()` es verdadero, solicitado con `postInvalidateOnAnimation()`. No hay contadores de tiempo de cuadro, FPS, asignaciones/frame ni telemetría; el código no permite afirmar FPS estables ni localizar el coste dominante sin una corrida medida.
- El primer acceso a un medio empaquetado copia el fichero completo a cache para `MediaPlayer`; la ruta es diferida y bufferizada, pero latencia de I/O y decodificación dependen del almacenamiento y dispositivo.

## Cambio medible implementado y límites

El exportador ahora transmite el APK plantilla y sus entradas ordinarias en bloques de 16 KiB. El manifiesto (que debe parchearse) y el icono siguen siendo pequeños buffers. La conversión de imágenes consulta bounds por ruta, aplica `inSampleSize` antes de decodificar y escribe a temporal; conserva el original salvo que WebP resulte estrictamente más pequeño. Esto elimina copias completas de plantilla/entradas/assets y el buffer de salida de imagen en RAM. No se midió la reducción en bytes, el pico de heap, el tiempo de exportación ni FPS: se requieren mediciones en dispositivos representativos y datos de proyectos V14.

## Pruebas pendientes

El workflow añade pruebas estáticas de regresión y, cuando se ejecute en CI, compila los APK, inspecciona min/target SDK, verifica la firma y los ZIP. En este sandbox no hay Android SDK/Build Tools, Java/Javac, adb ni emulador; por eso no se compiló aquí ni se generó el APK. El workflow tampoco se ha ejecutado todavía. Eso no sustituye instalar y ejecutar el editor y proyectos de prueba en Android 5.0 (API 21) y Android 8.1 (API 27). Tampoco se dispone de métricas de memoria, carga de assets o FPS en esos dispositivos; no se afirma todavía que la variante esté optimizada en hardware real.
