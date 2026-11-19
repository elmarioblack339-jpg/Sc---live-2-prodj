# Inspección de la APK original recuperada

## Identificación
- Archivo de usuario recuperado de Library: `app-debug(3)(1).apk`.
- Tamaño: 2,247,521 bytes.
- Copia `base.apk`: mismo tamaño y SHA-256; ambas son idénticas.
- SHA-256: `1df05cea2d059ab9aed953135ec4a983694dd5ce41192b82b5a6ab33ce6056ac`.
- Paquete conocido del proyecto: `com.sclive2.dj`.
- La APK se reconoce como paquete Android válido; no es un HTML renombrado.

## Contenido recuperado
- Archivo principal de interfaz dentro de APK: `assets/index.html`.
- Tamaño extraído: 84,352 bytes.
- Copia de trabajo extraída: `/mnt/data/sc-live2-original-apk/extracted/index.html`.
- APK original materializada en `/mnt/data/sc-live2-original-apk/app-debug(3)(1).apk`.
- La rama `sc-live2-rebuild-clean/rebuild-reference/index.html` que se creó antes contiene otro HTML recuperado de 132 KB; no es idéntico al HTML empaquetado en la APK. No tratar ambos como equivalentes ni reemplazar el origen sin revisar diferencias.

## Hallazgos concretos del código original
1. La APK ya contiene dos módulos SC AMPLIFICATION, LOW/BAJOS y MID/HIGH, POWER independiente y cuatro medidores/controles por banda.
2. El código original usa LOW 25–180 Hz y MID/HIGH 180 Hz–20 kHz. Incluye cuatro botones CORTE ON/OFF. Estos son comportamientos antiguos que el usuario ya pidió cambiar: BAJOS 25–125 Hz, corte automático coordinado y sin controles CORTE visibles.
3. El audio se divide mediante filtros Web Audio y tiene rutas independientes para BAJOS, MEDIO-BAJO, MEDIOS y BRILLO, con analizadores y controles de ganancia/mute. Revisar la cadena antes de modificarla para no romper audio.
4. El rack integrado en esta APK está rotulado explícitamente como dbx DriveRack PA2, con panel de apariencia PA2. Sus páginas HOME/GEQ/PEQ/XOVER/COMP/LIMITER/DELAY/SUB/AFS/UTILITY se presentan en la LCD, pero varios valores de navegación son de interfaz demostrativa; no asumir que todos los DSP son editables/funcionales.
5. La ruta DSP existente tiene dry/wet, EQ por filtros (LOW/BODY/PRESENCE/AIR) y compresor. La rueda cambia un valor general de pantalla; el código de páginas contiene valores de ejemplo. Esto requiere revisión y sustitución por el diseño SC RACK solicitado, basado en DriveRack 260.
6. La APK incluye ajustes de tema, tres presets EQ, perfiles CELULAR/PC, estado del dispositivo, bloqueo de UI, tamaño de carátula, fondo blanco/negro y reloj. Hay que preservar estas funciones mientras se implementan las correcciones acordadas.
7. El HTML extraído tiene un comentario CSS “dbx DriveRack PA2 inspired front panel” y títulos PA2 en el panel. No es la referencia correcta para el nuevo SC RACK; la imagen física correcta localizada en Library es `1000045026.webp`, frontal del DriveRack 260.

## Imágenes revisadas en Library
- `Interfaz DJ Neón SC LIVE 2.png`: referencia visual general de la consola y de los dos módulos SC AMPLIFICATION.
- `1000045026.webp`: fotografía frontal de dbx DriveRack 260; referencia física correcta para SC RACK.
- `1000045024.jpg`: captura previa de SC AMPLIFICATION con cuatro bandas, valores dB y medidores.
- `1000045025.jpg` y `1000045028.jpg`: capturas de perfiles de salida, temperatura, presets y parámetros de protección.
- `1000045023.jpg`: interfaz de rack anterior rotulada PA2; no confundir con el hardware 260.
- `1000045027.jpg`: fotografía de amplificador MTE CA32; referencia de equipo físico, no marca de SC AMPLIFICATION.
- `1000045021.jpg`: captura de los dos decks y Browser.

## Estado y siguiente criterio
- APK original recuperada e inspeccionada. Ya no es correcto decir que el adjunto no está disponible.
- La nueva rama ya contiene documentación de requisitos; el HTML actualmente ubicado en `rebuild-reference/index.html` todavía no ha sido sustituido por el asset exacto extraído de la APK.
- Antes de editar código, comparar la fuente extraída de la APK con el HTML alternativo y mantener el original intacto. No declarar funciones terminadas solo porque una compilación termina correctamente.


## Verificación de identidad del HTML
- SHA-256 del `assets/index.html` extraído de la APK: `c05f1fc97934229f8cb4bed3ba41e1e7e15ef4c78f3dba3730d8149b2bec214b`.
- SHA-256 del HTML conversacional de 132,186 bytes: `af1ed36ddde0f71f6b527a789099b44309f4da17a983e564d8cea46bfca6b32f`.
- Los archivos no son iguales. Por tanto, el `rebuild-reference/index.html` existente no es todavía la fuente exacta extraída de la APK original. Esta diferencia quedó verificada por comparación binaria, no por suposición.
