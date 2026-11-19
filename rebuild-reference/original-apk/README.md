# SC LIVE 2 — PUNTO DE GASOLINA

Esta rama es la nueva línea de trabajo basada en la APK original confirmada. Se creó desde el commit previo a los parches posteriores de SC AMPLIFICATION; esos commits posteriores no forman parte de esta rama.

## Referencia inmutable de la APK original
- Archivo: `app-debug(3).apk`
- Tamaño exacto: **2,247,521 bytes**
- SHA-256: `1df05cea2d059ab9aed953135ec4a983694dd5ce41192b82b5a6ab33ce6056ac`
- Paquete: `com.sclive2.dj`

## Fuente de interfaz extraída y verificada
- Ruta: `rebuild-reference/original-apk/assets/index.html`
- Tamaño: **84,352 bytes**
- SHA-256: `c05f1fc97934229f8cb4bed3ba41e1e7e15ef4c78f3dba3730d8149b2bec214b`
- La misma fuente se copia a `app/src/main/assets/index.html` durante la compilación.

## Regla de esta rama
No aplicar parches automáticos ni reutilizar HTML de compilaciones posteriores. La APK original permanece como referencia; las mejoras futuras se harán en commits nuevos y separados, sin modificar esta referencia.

## Nota sobre el archivo APK
La APK original se conserva como archivo binario verificado en el entorno de trabajo. Esta rama contiene su HTML extraído exacto y sus hashes de verificación; la copia binaria APK no se ha subido a GitHub en este commit.
