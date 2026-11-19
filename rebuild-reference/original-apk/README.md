# Fuente original única — SC LIVE 2

Esta carpeta se reserva exclusivamente para la APK que el usuario adjuntó en el chat: `app-debug(3).apk`.

## Archivo original recuperado
- Nombre recuperado de Library: `app-debug(3)(1).apk`
- Tamaño: 2,247,521 bytes
- SHA-256: `1df05cea2d059ab9aed953135ec4a983694dd5ce41192b82b5a6ab33ce6056ac`
- Paquete: `com.sclive2.dj`
- APK verificada como ZIP/APK válido.
- Asset principal extraído: `assets/index.html` (84,352 bytes)
- SHA-256 de ese HTML: `c05f1fc97934229f8cb4bed3ba41e1e7e15ef4c78f3dba3730d8149b2bec214b`

## Estado de incorporación
El APK original y su HTML extraído están recuperados y conservados en el entorno de trabajo de esta sesión. La copia binaria todavía no se ha transferido al repositorio GitHub mediante las acciones de archivos disponibles; por eso no se afirma falsamente que el APK ya esté guardado en esta carpeta remota.

**No usar HTML de otras versiones ni de otras apps.** El flujo de compilación ahora exige `original-apk/assets/index.html` y se niega a compilar si ese archivo falta. Esto impide que se vuelva a compilar accidentalmente la interfaz HTML alternativa.

Cuando se incorpore la fuente, la estructura prevista es:
- `original-apk/app-debug(3).apk` — APK original intacta.
- `original-apk/assets/index.html` — HTML extraído de esa APK.
- `../REFERENCIAS_Y_REQUISITOS.md` — instrucciones de diseño y comportamiento.
- `../INSPECCION_APK_ORIGINAL.md` — hallazgos técnicos de la inspección.
