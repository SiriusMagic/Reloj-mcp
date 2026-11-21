# Arquitectura de Reloj

Carpeta = package (`android/app/src/main/java/com/zirakusa/reloj/`):

| Carpeta | Qué hay |
|---|---|
| `data/` | Lógica pura, con pruebas. `Alarm` (hora, días, fecha, desactivar al sonar, `Sound`, etiqueta, posponer, `Vibration`, posposición en curso) y `AlarmStore` (JSON en las preferencias «reloj»); `SoundFiles` (nombres seguros de los sonidos añadidos); `Schedule` (próximo sonido con festivos y posposición, `Until` «sonará en», `Repeat` «lun a vie»…) y `Holidays` (feriados de Ecuador con Carnaval y Viernes Santo desde la Pascua); `Timers` (`CountDown`, `Stopwatch`, `Preset`, `Format`); `Cities` (catálogo A-Z, búsqueda sin acentos, ciudad o zona IANA); `Settings` (ajustes tipados sobre `KeyValues`). |
| `ui/` | `ClockState` (único dueño del estado: pestaña y página, alarmas y la que se edita, ciudades, temporizador, cronómetro y ajustes) y `RelojApp` (barra flotante: Alarma, Reloj mundial, Temporizador, Cronómetro; páginas encima; `AppActions`, `SoundSource`, `Issue`). |
| `ui/screens/` | `AlarmsScreen`, `AlarmEditor` y `EditorPages` (Repetir, Sonido con «Añadir nuevo», Vibrar, Posponer), `Wheel` (ruedas de hora y fecha), `WorldScreen` (reloj analógico ↔ digital al tocarlo, ciudades) y `CityPicker` (A-Z con índice y buscador), `TimerScreen` (ruedas, temporizadores guardados, anillo, «Agregar temporizador»), `StopwatchScreen` (vueltas), `SettingsScreen` y `SettingsParts`, `McpScreen` (igual que en las apps hermanas), `RingScreen` (alarma sonando), `Texts` (textos con el idioma del teléfono) y `Chrome`. Solo pintan y avisan. |
| `ui/theme/` | `Theme`: `ThemeMode` (Sistema, Claro, Oscuro azul marino, Negro puro de fábrica), `Accent` (naranja de fábrica…) y `ClockColors`. |
| `mcp/` | Modo MCP, como las apps hermanas: `Tokens` (`rel_…`, cifrados con `SecretBox`), `ClockTools` (`clock_alarms`, `clock_next_alarm`, `clock_world_time`; con escritura `clock_alarm_create`, `clock_alarm_enable`, `clock_alarm_delete`; a través de `ClockData`), `McpHandler`, `RelayLink` (relay de Stone), `McpMode` (`LiveData` guarda, reprograma y avisa a la pantalla) y `HubLink` (módulo `clock_`). |
| `AlarmScheduler.kt` | `AlarmScheduler.sync` (cada alarma como `setAlarmClock`, el temporizador como alarma exacta; cancela las que ya no suenan) y `AlarmReceiver` (suena; al arrancar o cambiar la hora, reprograma). |
| `RingService.kt` / `Ringing.kt` | Servicio en primer plano que suena (volumen creciente), vibra y muestra la notificación de pantalla completa con Posponer y Detener; se pospone solo tras «Silenciar tras». `Ringing` guarda lo que suena y qué hacen Posponer y Detener. |
| `RingActivity.kt` | La alarma sonando sobre la pantalla de bloqueo. |
| `Sounds.kt` | Tonos de alarma del teléfono, sonidos propios (copiados a `files/sounds`, máx. 50 MB) y `Player`. |
| `Summary.kt` | `SummaryLines` (pura, con prueba) y `SummaryProvider`: el resumen de familia para Inicio, solo para apps con la misma firma. |
| `MainActivity.kt` | Crea el estado, reprograma, «Añadir nuevo» (Filem primero, si no las apps de archivos), permisos (notificaciones, pantalla completa, alarmas exactas) e «Ir al Hub». |

Reglas:
- Texto visible en `res/values/strings.xml` (español) y `values-en` (los nombres de ciudades son datos, en español; el
  país sale en el idioma del teléfono).
- Colores siempre de `ClockColors`.
- Archivos de pantalla de unas 150 líneas como mucho; si crece, se divide.
- `ScreenshotsTest` usa un reloj fijo (sábado 21 nov 2026, 6:36:40, Ecuador) y `RelojApp(ticking = false)`.
