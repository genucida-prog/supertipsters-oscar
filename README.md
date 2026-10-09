# Super Tipsters Oscar

Aplicación Android (APK) con predicciones de fútbol, cuotas y combinadas.

## Instalación

Descarga el APK desde [Releases](https://github.com/genucida-prog/supertipsters-oscar/releases) e instálalo. Android pedirá permitir instalar apps de orígenes desconocidos para este navegador/gestor de archivos.

## Autoactualización

La app comprueba, al abrirla, la última publicación de este repositorio. Si hay una versión más reciente, muestra un diálogo para descargarla e instalarla.

## Funciones

- Pronósticos 1X2, más/menos de 2,5 y 1,5 goles, y ambos marcan, con modelo propio.
- Combinadas automáticas y «mi combinada», con cuota conjunta por casa.
- **Cartera**: balance, ROI y aciertos por mercado y liga de lo que apuestas.
- **Historial de combinadas** guardadas y evaluadas con resultados reales.
- **Filtro de confianza mínima** por pata.
- **Widget** en la pantalla de inicio y **aviso de fin de jornada** (Android).
- Botón «Buscar versión nueva» y actualización automática desde Releases.

## Código y compilación

El proyecto Android está en `android/`. Requiere JDK 17 y el Android SDK 34.

```bash
cd android
./gradlew assembleDebug    # APK de prueba, sin firma de release
```

Cada push a `main` (y cada pull request) compila el APK de depuración en GitHub
Actions; el resultado queda como artefacto `app-debug`.

## Firma

La APK de release se firma con un keystore propio (no incluido en el repositorio).
Sin `android/keystore.properties` la tarea `assembleRelease` compila igualmente,
pero sin firmar: por eso la integración continua solo ejecuta `assembleDebug`.
