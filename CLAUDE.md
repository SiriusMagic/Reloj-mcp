# Reloj (reloj y alarmas MCP): reglas para agentes

- Lee `ARQUITECTURA.md` y abre solo los archivos que necesites.
- Pruebas: `cd android && ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease` (debe pasar todo).
  Nunca desactives ni saltes una prueba. Lint sin avisos nuevos.
- Cada versión: sube `versionCode` y `versionName`, copia el APK a `android/Reloj-<versión>.apk` (borra el anterior)
  y anótalo en `CHANGELOG.md`.
- Reglas puras (próxima alarma, «sonará en», repetir, festivos de Ecuador, temporizador, cronómetro, ciudades, nombres
  de sonidos) en `data/`, sin Android y con pruebas. Las pantallas solo pintan y avisan.
- Toda alarma pasa por `AlarmStore` y, tras cada cambio, por `AlarmScheduler.sync` (también desde MCP y al sonar).
- «Añadir nuevo» abre Filem (`com.zirakusa.filem`, «Elegir archivo») si está instalado; si no, las apps de archivos del
  teléfono. El sonido se copia dentro de Reloj (`files/sounds`).
- Privacidad: alarmas, ajustes y sonidos viven solo en el teléfono (fuera de copias). Nada sale salvo por el Modo MCP
  que el usuario active; internet es solo para eso.
- Cada herramienta MCP nueva lleva su prueba en `McpTest`: lista de herramientas, llamada correcta, argumentos malos y
  «no encontrado». Las que cambian algo exigen token de lectura y escritura (`READ_ONLY` si no).
- Prohibido: `eval`, motores de scripts, SQL concatenado.
- Firma: la de depuración del entorno, igual que Stone, Luma, Calculadora, Notas, Filem, Nivel y Gateway Hub.
