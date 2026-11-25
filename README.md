# CleanZip

App Android sencilla para **abrir archivos comprimidos y descomprimirlos** en la carpeta que tú elijas.

## Descargar

📲 **[Descargar CleanZip.apk](https://github.com/manlorhid/CleanZip/releases/latest/download/CleanZip.apk)**

Para instalarla, abre el archivo en el móvil y, si Android lo pide, permite instalar apps de origen desconocido.
Requiere Android 8.0 o superior.

## Cómo se usa

1. **Elige un archivo** comprimido. Verás su contenido.
2. **Elige la carpeta** donde quieres guardar lo extraído.
3. Pulsa **Descomprimir**. Se crea una carpeta con el nombre del archivo.

La primera vez que abres la app aparece un pequeño tutorial con estos pasos.

## Qué incluye

- Formatos: **ZIP, 7z, RAR, TAR, TAR.GZ, TAR.BZ2, TAR.XZ, GZ, BZ2 y XZ**.
- Tema **claro, oscuro** o según el sistema.
- Abrir archivos directamente desde el gestor de archivos («Abrir con CleanZip»).

No admite archivos con contraseña ni RAR5.

## Anuncios (AdMob)

La app muestra un banner en la parte inferior. Ahora usa los **ID de prueba de Google** (anuncios de ejemplo).

**Para usar tus ID reales**, edita `app/build.gradle.kts`:

```kotlin
manifestPlaceholders["admobAppId"] = "ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY"   // ID de la app
buildConfigField("String", "ADMOB_BANNER_ID", "\"ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ\"")  // ID del bloque de anuncios
```

Antes de publicar en Google Play:
- En AdMob, crea el **mensaje de consentimiento GDPR** (Privacidad y mensajes → GDPR) y publícalo.
- Sube [`docs/app-ads.txt`](docs/app-ads.txt) (con tu ID de editor) a la raíz de tu web.
- Publica la [política de privacidad](PRIVACY.md) en una URL pública y enlázala en la ficha de Play.
- En Play Console: marca **"Contiene anuncios"**, rellena **Seguridad de los datos** (ID de publicidad, datos del dispositivo e
  interacciones, compartidos con Google para publicidad) y declara el uso del **ID de publicidad**.

## Quitar anuncios y donaciones (Google Play Billing)

Desde el icono ♡ de la app se puede **quitar los anuncios con un pago único** o **hacer una donación**.
Las donaciones usan Google Play porque Google Play no permite enlaces de pago externos (PayPal, Ko-fi...) en las apps.

Crea estos productos en **Play Console → Monetizar → Productos → Productos de compra única** (los ID deben ser exactos):

| ID | Tipo | Precio sugerido |
|---|---|---|
| `remove_ads` | Quitar anuncios (no consumible) | 1,99 € |
| `donation_small` | Donación | 0,99 € |
| `donation_medium` | Donación | 2,99 € |
| `donation_large` | Donación | 4,99 € |

Si quieres otros ID, cámbialos en `app/src/main/java/com/cleanzip/app/billing/BillingManager.kt`.

Para que las compras funcionen hace falta: una **cuenta de pagos** en Play Console, subir la app al menos a
**prueba interna** e instalarla desde Play, y añadir tu correo en **Configuración → Pruebas de licencias** para probar sin pagar.

## Compilar el código

Abre el proyecto con **Android Studio** y pulsa ▶ *Run*, o desde la terminal:

```bash
./gradlew assembleRelease
```

Cada cambio en `main` compila el APK automáticamente y lo publica en *Releases*.
