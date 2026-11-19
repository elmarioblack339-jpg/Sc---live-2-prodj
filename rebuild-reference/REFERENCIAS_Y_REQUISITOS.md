# SC LIVE 2 — referencias y requisitos confirmados

Esta carpeta es el espacio de reconstrucción. La aplicación original del usuario es la referencia principal; este documento reúne requisitos recuperados del historial y de las imágenes guardadas. No sustituir la aplicación por una interfaz genérica ni tratar un HTML como si fuera equivalente a la APK sin comprobarlo.

## 1. Fuente de verdad y preservación
- APK original recuperada del historial/Library: `app-debug(3)(1).apk` (subida por el usuario, 2,247,521 bytes); `base.apk` es idéntica byte por byte. SHA-256: `1df05cea2d059ab9aed953135ec4a983694dd5ce41192b82b5a6ab33ce6056ac`. Se extrajo `assets/index.html` (84,352 bytes) y se inspeccionó. La fuente original ahora sí está disponible en el entorno de trabajo en `/mnt/data/sc-live2-original-apk/`; no confundirla con los APKs de compilación generados por el asistente.
- HTML recuperado de conversación: `SC_LIVE_2_DJ_de_Mario_parlantes_reactivos.html`. Es una referencia de código auxiliar, no sustituto demostrado de la APK.
- Rama de trabajo: `sc-live2-rebuild-clean`; rama previa `sc-live2-build` se conserva como respaldo. No borrar ramas, repositorio ni fuentes antiguas.
- Esta carpeta debe recibir las referencias, documentación y código nuevo; preservar el material de origen y mantener cambios rastreables.

## 2. Interfaz principal
- Conservar la identidad y composición de SC LIVE 2: pantalla horizontal completa, dos decks completos, jog wheels, ondas, reproducción/pausa, CUE, Hot Cues, pitch y controles centrales.
- No recortar ni ocultar decks, ecualizador paramétrico/gráfico ni controles. Sin franja blanca sobrante; el diseño debe ocupar correctamente la pantalla.
- Carátula/splash principal elegida: `Pantalla DJ neón SC Live 2.png` (vinilo, corona, audífonos, azul/púrpura). No reemplazarla por otra.
- Referencia de composición de consola/amplificadores: `Interfaz DJ Neón SC LIVE 2.png`. Sus rótulos de marca MTM/MT-410 son elementos de la imagen de referencia, no deben sustituir el nombre SC AMPLIFICATION ni convertirse en branding de la app.

## 3. SC AMPLIFICATION — diseño y comportamiento
- Mostrar dos módulos independientes dentro de la interfaz, con acabado metálico, rejillas, tornillos y luces neón: izquierda azul LOW/BAJOS; derecha púrpura MID/HIGH.
- Módulo LOW: POWER independiente y perillas BAJOS y MEDIO-BAJO. Rango solicitado para BAJOS: 25–125 Hz. El ajuste de graves debe coordinar el corte correspondiente en medios-bajos automáticamente; no mostrar un botón CORTE ON/OFF.
- Módulo MID/HIGH: POWER independiente y perillas MEDIOS y BRILLO. Conservar las frecuencias de trabajo ya definidas para esas bandas; no añadir botones CORTE ON/OFF.
- Controles centrados en 0 dB: izquierda resta, derecha aumenta. Apagar POWER de un módulo silencia solo sus bandas.
- Medidores independientes por banda (BAJOS, MEDIO-BAJO, MEDIOS y BRILLO), vinculados a señal real filtrada de cada banda; LEDs pequeños verde/amarillo/rojo. No usar el volumen maestro como sustituto de la señal por banda.
- No usar marcas MTCA/MTE como branding de los módulos; el nombre visible es SC AMPLIFICATION.

## 4. SC RACK — referencia física correcta
- El procesador físico de referencia es **dbx DriveRack 260**, no DriveRack PA2. Imagen localizada en la biblioteca: `1000045026.webp` (panel frontal DriveRack 260, pantalla LCD, DATA EDIT/PUSH, botones y medidores verticales).
- La imagen `Unidad dbx DriveRack PA2 en estudio.png` corresponde a PA2 y NO es el modelo físico de referencia para SC RACK. No confundir modelos.
- Crear la versión de software con marca **SC RACK**, inspirada en el formato físico del DriveRack 260, no una copia con marca dbx.
- Apariencia: unidad rack 1U, LCD legible, rueda DATA EDIT/PUSH, botones físicos, cinco medidores LED verticales verde/amarillo/rojo.
- DATA WHEEL debe girar para navegar/cambiar valores y presionarse para seleccionar/confirmar/guardar. Controles funcionales, no decorativos.
- Módulos DSP solicitados: HOME, GEQ, PEQ, XOVER, COMP, LIMITER, DELAY, SUB, AFS y UTILITY.
- GEQ con bandas bajo, medio-bajo, medios y brillo; EQ profunda por bandas y paramétrica. Mantener el ecualizador paramétrico principal existente como función separada; SC RACK añade procesamiento adicional sin duplicar ni saturar controles.
- El rack debe procesar realmente la señal de audio, con límites seguros; no presentar botones o medidores falsos.

## 5. Protección, salida y rendimiento
- Controles de ganancia de graves/medios/agudos, umbral de limiter, ratio y saturación suave con bloqueo de parámetros: bloqueados en gris, sin cambios, preservando valores y sonido.
- Perfiles SALIDA CELULAR y SALIDA PC + NIVEL; tres espacios de preset; modo ULTRA; CPU y temperatura.
- En celular, selector manual de buffer con valores que soporte el motor/dispositivo y explicación de latencia frente a estabilidad.
- PC + NIVEL simula un perfil de procesamiento más fuerte dentro de límites reales del teléfono; no afirmar que el teléfono se convierte en PC ni aumentar la salida peligrosamente.
- Si Android detecta temperatura alta, reducir carga visual/CPU y proteger la reproducción. Los limitadores de amplificadores y SC RACK deben controlar picos/distorsión para proteger parlantes; el limiter no enfría el teléfono.
- Al bajar la temperatura, mantener los limitadores activos hasta que el usuario los desactive manualmente.
- Presets persistentes entre actualizaciones: tres slots, lápiz para editar nombre, guardar/reemplazar deliberadamente y recuperar parámetros. Migrar datos antiguos sin borrarlos.

## 6. Decks, ondas y navegación
- Tocar el área de onda no abre Biblioteca/Browser; solo la línea verde de progreso permite scrub. Hacer la barra de progreso algo más visible si cabe.
- Mover BACK/FWD junto a Browser dentro del marco, sin invadir ondas/frecuencias. Mover solo BACK y el botón contiguo de navegación; conservar carátula y controles musicales.
- Browser solo abre con el botón BROWSER.
- TRACK SKIP: flechas anterior/siguiente bajo ese rótulo cambian la canción anterior/siguiente de la lista del deck correspondiente; índices independientes por deck.
- Flechas inferiores de avance/retroceso dentro de canción: retroceso 10 segundos y avance 30 segundos. No confundirlas con TRACK SKIP.
- Cargar canción en deck vacío o detenido/pausado, nunca sobre uno reproduciéndose ni interrumpir el otro deck.
- Si se intenta reemplazar pista del deck que está reproduciendo, preguntar: “Una canción se encuentra sonando o reproduciéndose en curso en este deck. ¿Deseas cambiarla? Sí o No.” Sí reemplaza solo ese deck; No conserva la pista actual.
- Browser web/YouTube y decks virtuales deben mantenerse independientes de los decks locales; no prometer reproducción universal en segundo plano sin probar WebView, foco de audio y restricciones Android.
- Jog/scratch debe responder al movimiento del dedo como un vinilo real, no solo acelerar/desacelerar ni añadir un efecto separado. No asumir comportamiento de scratch con deck detenido sin confirmar.
- Tocar el área blanca libre alrededor de la carátula alterna fondo blanco/negro; segundo toque vuelve a blanco. No interferir con controles, ondas ni jogs.

## 7. Funciones y datos existentes que no deben perderse
- Las canciones locales deben reproducirse sin internet.
- Carga/búsqueda nunca debe detener ni borrar la pista que está sonando.
- Mantener datos, presets y ajustes compatibles al actualizar.
- Reloj y fecha toman automáticamente hora/zona del teléfono; permitir 12 h AM/PM o 24 h.
- Botón de candado junto a Configuración bloquea diseño, escala/zoom y orientación actuales; desbloqueado permite cambios.
- No añadir animación 3D por ahora.

## 8. Referencias visuales localizadas
- `Interfaz DJ Neón SC LIVE 2.png`: composición visual de consola y dos módulos amplificadores.
- `Pantalla DJ neón SC Live 2.png`: carátula/splash principal elegida.
- `1000045026.webp`: fotografía frontal del dbx DriveRack 260; referencia correcta del SC RACK.
- `1000045024.jpg`: captura del diseño previo de SC AMPLIFICATION; muestra los cuatro controles, estados de POWER, valores dB y medidores por banda.
- `1000045025.jpg` y `1000045028.jpg`: capturas de salida de audio, estado térmico, presets y controles de protección.
- `1000045023.jpg`: captura de una propuesta anterior rotulada DriveRack PA2; sirve como referencia de una interfaz previa, pero no cambia el modelo físico objetivo DriveRack 260.
- `1000045027.jpg`: fotografía de amplificador MTE CA32; referencia visual de equipo físico, no branding para SC AMPLIFICATION.
- `1000045021.jpg`: captura de interfaz DJ con Browser abierto, útil para recordar distribución y controles.

## 9. Criterio de aceptación
No declarar una función terminada solo porque compila. Verificar compilación, instalación, visualización en móvil, funcionamiento real de audio, persistencia de presets y que ningún módulo recorte/oculte decks. Registrar qué se comprobó y qué queda pendiente. No reemplazar la APK original por una reconstrucción especulativa.
