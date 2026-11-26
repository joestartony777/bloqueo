# BLOQUEO para Android — beta nativa 0.2.2

**Estado: código fuente preparado para compilar, no APK compilado ni probado en teléfono.** El entorno donde se preparó este proyecto no dispone del SDK de Android, Gradle ni acceso a los paquetes de compilación, por lo que no se puede afirmar que la compilación o el bloqueo funcionen en un dispositivo real. El workflow de GitHub Actions incluido permite obtener un APK de depuración al ejecutarse en un entorno que sí tenga Internet.

## Qué se conserva de BLOQUEO v0.9.19

- Nombre, personalidad visual, círculos provisionales para compañeros/iconos, cinco compañeros, tema claro/oscuro, preguntas y flujo de onboarding.
- Nombre del compañero personalizable durante la selección y editable después desde la pantalla principal; se guarda localmente.
- En la selección manual, preselecciona hasta tres redes sociales con mayor tiempo de uso de los últimos siete días, si el usuario autoriza el acceso al uso de Android; muestra los tiempos detectados y permite modificar la selección.
- Selección entre recomendaciones y elección manual, resumen, selección de aplicaciones, opciones para dejar mensajería disponible y sesiones de enfoque.
- Los horarios 24/7, de lunes a viernes y personalizados por días/horas, adaptados a Android.
- El catálogo **original** de 59 plataformas y dominios de Windows se incluye en `app/src/main/assets/catalogo_bloqueo_escritorio.json` y el archivo de origen en `shared/blocking_original_windows.go`, para no perder esa lógica/datos al continuar el desarrollo. **Ese catálogo no se usa todavía como filtro de red Android.**

## Bloqueo real implementado (con limitaciones)

Durante un horario activo o una sesión, un `AccessibilityService` al que el usuario debe dar permiso explícito puede detectar **solo el nombre de paquete** de una app abierta y ejecutar la acción global de volver a Inicio si ese paquete está en la selección. El servicio no lee mensajes, texto, teclas ni capturas de pantalla. Se excluyen la propia app y SystemUI. La lista se guarda localmente, sin cuentas ni servidores. Mensajería puede quedar exceptuada si el usuario activa esa opción.

**Esto no es un bloqueo inviolable.** Android puede impedir o suspender el servicio; el usuario puede desactivarlo, salir de la app o desinstalarla. Algunas aplicaciones podrían no bloquearse según el fabricante y la versión del sistema. Solo bloquea apps identificadas por paquete, no webs abiertas dentro de Chrome.

### Funciones pendientes respecto a Windows y a la visión final

- Bloqueo de sitios web, DNS y contenido adulto (se necesita integrar una solución Android específica, posiblemente VPN local y reglas de dominios, con pruebas de fugas y permisos).
- Estadísticas avanzadas, sincronización PC/móvil, bloqueo resistente a desinstalación y filtros por contenido dentro de apps.
- Pulido completo de ilustraciones, animaciones y distribución responsive; el diseño móvil reutiliza la jerarquía y los textos, pero no puede ejecutar el renderizador Direct2D de Windows.

La beta **no muestra como bloqueado** el contenido adulto, porque aún no lo filtra. No prometas esa protección a usuarios finales antes de implementarla y probarla.

## Generar APK sin instalar herramientas en tu ordenador (GitHub Actions)

1. Crea un repositorio privado nuevo en GitHub y sube los archivos de esta carpeta, manteniendo `.github/workflows/android-apk.yml` en la raíz.
2. Abre la pestaña `Actions` del repositorio y selecciona **Compilar APK Android**.
3. Pulsa `Run workflow` y abre la ejecución recién iniciada. Si termina correctamente, en `Artifacts` descarga **BLOQUEO-Android-debug-apk**.
4. Descomprime el archivo descargado: dentro está `app-debug.apk`. Envíalo a tu celular Android y abre el APK para instalarlo (autoriza instalaciones desde esa fuente solo si confías en el archivo).
5. Abre BLOQUEO, configura apps/horario; en la pantalla principal toca **Abrir Ajustes de accesibilidad** y activa el servicio de BLOQUEO tras leer la descripción del permiso. En Android 13+ las apps instaladas fuera de una tienda pueden requerir **Permitir ajustes restringidos** desde la información de la app.

No se requiere conectar un servidor de BLOQUEO. Sí se requiere conexión a Internet y una cuenta de GitHub para ejecutar la compilación remota.

## Con Android Studio

Abre la carpeta como proyecto Android Gradle, instala `platforms;android-35` y `build-tools;35.0.0`, configura Gradle 8.10.2 y usa **Build > Build APK(s)**. El APK debug estará en `app/build/outputs/apk/debug/app-debug.apk`. Este proyecto intencionadamente no incluye binarios de Gradle wrapper, porque no podían descargarse en el entorno de creación.

### Requisitos

- Android mínimo 8.0 (API 26); Java 17; AGP 8.7.3, Gradle 8.10.2, compile/target SDK 35.
- ID de paquete `com.bloqueo.mobile`, `versionName 0.2.0`.
- Proyecto sin librerías externas de terceros.
- No se pide permiso de Internet, ubicación, micrófono, cámara, contactos ni contenido de ventana.

### Seguridad y privacidad

El servicio de accesibilidad no está diseñado para personas con discapacidad; declara `isAccessibilityTool=false`. Para distribuir en Google Play hará falta revisar las reglas vigentes de accesibilidad, ofrecer explicación y consentimiento explícito, y evaluar alternativas técnicas. No fuerces permisos ni impidas que el usuario desactive la protección.
