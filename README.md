# Mi Garmin (app Android)

App nativa (Kotlin + Jetpack Compose) con la misma información que tu dashboard de Claude:
Resumen del año (km desde el 1 de enero, media mensual, mes en curso con proyección), Sueño y recuperación,
Entrenamiento (km por mes y semana, ¿mejoro o empeoro?, rendimiento en bici, eficiencia W/lpm, VO2 máx.,
minutos de intensidad, lista de actividades), Diario (pasos) y Cuerpo (peso y estrés).

Lee los datos **directamente de Garmin Connect** desde el móvil; no pasan por ningún servidor intermedio.

## Cómo ejecutarla

1. Instala **Android Studio** (versión reciente, con JDK 17 o superior).
2. `File → Open` y elige esta carpeta (`garmin-android`). Deja que termine el "Gradle sync"
   (descarga las dependencias; necesita internet la primera vez).
3. Conecta el móvil con la **depuración USB** activada (o crea un emulador) y pulsa **Run ▶**.
   Para un APK instalable: `Build → Build Bundle(s) / APK(s) → Build APK(s)`.

## Cómo conectarla con tu cuenta

La app no pide usuario ni contraseña: reutiliza la sesión que ya creaste en el PC con `login.py`.

1. En el PC abre `C:\Users\<tu usuario>\.garminconnect\garmin_tokens.json` y copia todo su contenido.
2. Pásalo al móvil de forma privada (por ejemplo, copiar y pegar con el portapapeles compartido o
   enviártelo a ti mismo) y bórralo después del chat/correo: **equivale a tu contraseña**.
3. En la app: pega el contenido y pulsa **Conectar**. Se guarda cifrado (Android Keystore) y la app
   renueva el acceso sola. Si algún día caduca, repite el paso con un `garmin_tokens.json` nuevo
   (ejecutando de nuevo `python login.py` en el PC).

## Notas

- Usa la API **no oficial** de Garmin Connect (la misma que la app oficial). Si Garmin cambia algo,
  alguna pantalla podría dejar de cargar; la app muestra los avisos en el Resumen.
- Al abrir y al pulsar ⟳ se descargan: 28 noches de sueño, 8 semanas de HRV, FC en reposo y pasos,
  26 semanas de intensidad y estrés, ~7 meses de VO2 máx., 13 meses de peso y todas las actividades del año.
- Las gráficas se pueden tocar o arrastrar para ver el valor de cada día.
- Todo es de solo lectura: la app no modifica nada en tu cuenta.
- El código está en `app/src/main/java/com/carlos/garmin`:
  `GarminClient.kt` (conexión y renovación del token), `Repository.kt` (descarga),
  `Metrics.kt` (cálculos: km por mes, eficiencia, tendencias) y `ui/` (pantallas y gráficas).

## Novedades de esta versión

- **Inicio configurable** (primera pestaña): recomendación de hoy según tu descanso y carga, franja de estado,
  Ayer, Esta semana, Mes en curso y Este año. Más bloques opcionales: última actividad, objetivo anual,
  ¿mejoro o empeoro? y rango de 7/30/90 días.
- **Personalizar inicio** (icono del lápiz): ordenar bloques con las flechas, ocultarlos y elegir qué cifras ver en cada uno.
- **Ajustes**: módulos visibles (pestañas), bloques dentro de Entreno, tema claro/oscuro/sistema, color principal y de
  resalte, y la conexión con la cuenta de Garmin (pegar `garmin_tokens.json`, cerrar sesión). Sin credenciales, la app abre Ajustes.
- Paleta en tonos de azul. Los ajustes se guardan en el móvil.
- La recomendación usa reglas sencillas (sueño, VFC, pulso en reposo, esfuerzo de ayer y carga 7/28 días); es orientativa.

No incluido todavía: entrenamiento programado, récords y logros, mapa del recorrido, tiempo en zonas y equilibrio de carga de 4 semanas.

## Compilar con GitHub (sin Android Studio)

1. Crea un repositorio privado en GitHub y sube el contenido de esta carpeta (incluida `.github`).
2. En la pestaña **Actions** se ejecuta "Compilar APK" (o lánzalo con *Run workflow*).
3. Al terminar, descarga el artefacto **mi-garmin-apk**, descomprime y copia `app-debug.apk` al móvil.
4. En el móvil permite instalar apps de origen desconocido para el gestor de archivos y abre el APK.
