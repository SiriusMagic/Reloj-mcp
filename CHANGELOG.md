# Reloj 0.3.0

- **Órdenes estándar de Android** (FAMILIA.md §10): «poner alarma» (hora, minutos, nombre y días), «poner temporizador»,
  «ver alarmas» y «ver temporizadores». Las usa el Asistente de la familia y cualquier asistente o app con el permiso
  `SET_ALARM`. Pedir dos veces la misma alarma no la duplica: la enciende. Con «sin mostrar» solo avisa y se cierra.
- 23 pruebas.

# Reloj 0.2.0

- **Resumen de familia**: la tarjeta de Reloj en la pantalla «Hoy» de Inicio (próxima alarma con su etiqueta, temporizador
  y cronómetro en marcha). Lo da `SummaryProvider` (`${applicationId}.summary`), solo a apps con la misma firma.
- 21 pruebas.

# Reloj 0.1.0

Primera versión: reloj con alarmas, reloj mundial, temporizador y cronómetro, en negro con acento naranja.
- **Alarma:** lista con «sonará en…»; editor con ruedas, Repetir, fecha específica, desactivar al sonar, sonido,
  etiqueta, posponer (5–30 min, 1–10 veces) y vibración (Estándar, Pulso, Rápida, Latido, Vals, Olas, Urgente, Ninguno).
- **Añadir nuevo** sonido: abre Filem («Elegir archivo», Filem 0.3.0) o, si no está, las apps de archivos del teléfono;
  el audio se copia a Reloj (hasta 50 MB) y se puede quitar.
- Suena con `setAlarmClock` (exacta y en reposo), servicio en primer plano, pantalla sobre el bloqueo, volumen
  creciente, Posponer/Detener y posposición automática; reprograma al arrancar y al cambiar hora o zona. Avisa si faltan
  notificaciones, pantalla completa o alarmas exactas.
- **Reloj mundial:** analógico ↔ digital al tocarlo; ~110 ciudades con buscador sin acentos e índice A-Z.
- **Temporizador** con guardados y sonido propio; **Cronómetro** con vueltas.
- **Ajustes:** festivos de Ecuador (fechas de ley, Carnaval y Viernes Santo calculados), volumen creciente, Silenciar
  tras, sonidos, Modo MCP y Temas.
- **Modo MCP** igual que las apps hermanas (tokens `rel_…`, Redireccionar a Hub, módulo `clock_`), con 6 herramientas.
- Datos fuera de las copias en la nube. 20 pruebas (lógica, MCP y capturas).
