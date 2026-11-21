# Reloj (reloj y alarmas MCP)

Reloj, alarmas, reloj mundial, temporizador y cronómetro para Android, en negro con acento naranja. Parte de la familia
de Stone, Luma, Calculadora, Notas, Filem y Nivel: tu IA (Claude, ChatGPT…) puede **leer y crear tus alarmas** por MCP.

## Estado (0.1.0)

- **Alarma:** lista con interruptor y «lun a vie. La alarma sonará en 1 día»; tocar edita, mantener elimina.
  «Agregar alarma» con ruedas (12 o 24 h según el teléfono), Repetir (días), Sonar en fecha específica (ruedas de mes,
  día y año), Desactivar alarma una vez suene, Sonido de la alarma, Etiqueta, Posponer (minutos y veces) y Vibrar
  mientras suena (7 patrones o ninguno).
- **Sonido de la alarma:** «Sus sonidos» con **Añadir nuevo** (abre **Filem** si está instalado; si no, las apps de
  archivos del teléfono) y los tonos de alarma del dispositivo, con escucha previa. Los sonidos añadidos se copian
  dentro de Reloj.
- **Sonar:** a la hora exacta aunque el teléfono duerma, sobre la pantalla de bloqueo, con volumen que sube poco a poco,
  Posponer y Detener; se pospone solo tras 1–30 min. Reprograma al reiniciar o cambiar la hora.
- **Reloj mundial:** reloj analógico; al tocarlo pasa a digital con segundos («6:40:54 a. m.»), con la fecha y «hora de
  Ecuador». Ciudades con la diferencia horaria; «Seleccionar ciudad» de la A a la Z con índice y «Buscar ciudades».
- **Temporizador:** ruedas h/m/s, temporizadores guardados (Reunión, Sueño, Ejercicio y los que agregues con nombre),
  anillo con lo que falta, pausa, +1:00 y su propio sonido (campana).
- **Cronómetro:** centésimas, vueltas (la más rápida y la más lenta marcadas).
- **Ajustes:** Días festivos (Ecuador), subir el volumen poco a poco, Silenciar tras, sonidos del temporizador y del
  cronómetro, **Modo MCP**, Temas (Sistema, Claro, Oscuro, Negro y color de acento), privacidad y versión.
- **Modo MCP:** `clock_alarms`, `clock_next_alarm`, `clock_world_time` y, con token de lectura y escritura,
  `clock_alarm_create`, `clock_alarm_enable`, `clock_alarm_delete`. Mismo relay y Gateway Hub (módulo `clock_`) que las
  demás apps.
- Sin anuncios ni estadísticas; internet solo para el Modo MCP (apagado de fábrica).

APK: [`android/Reloj-0.1.0.apk`](android/Reloj-0.1.0.apk) (`com.zirakusa.reloj`, firmado como Stone y Gateway Hub).
Para «Añadir nuevo» con Filem hace falta Filem 0.3.0 o posterior.

## Compilar y probar

```bash
cd android
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```

## Siguiente

Ver [`ROADMAP.md`](ROADMAP.md).
