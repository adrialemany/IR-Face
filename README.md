# IR Face — desbloqueo facial por infrarrojos (v0.3)

Xiaomi Mi 8 (dipper) · LineageOS 22 · Magisk. Sin recompilar LineageOS.

## Cómo funciona

- **Hardware:** cámara 5 (OV7251, infrarrojos) + emisor `/sys/class/leds/ir_torch`.
- **Reconocimiento:** YuNet (detección) + SFace (reconocimiento), todo en el teléfono.
- **Desbloqueo:** IR Face es un *agente de confianza* con **Active Unlock**, el mecanismo
  oficial de Android 13+ que usa Google para desbloquear con el reloj:
  1. Cada vez que desbloqueas (PIN, huella o cara), IR Face concede confianza "renovable".
  2. Al bloquearse el teléfono, Android la deja en espera.
  3. Al encender la pantalla, IR Face enciende el emisor ~3 s, te reconoce y renueva la
     confianza pidiendo quitar la pantalla de bloqueo.
- **Seguridad (igual que Android exige a cualquier desbloqueo "débil"):** tras reiniciar y tras
  los plazos de seguridad de Android (horas sin usarlo), pide PIN o huella una vez.
  No sirve para apps de banca/pagos que exigen biometría fuerte (MIUI tampoco lo permitía).
- **Módulo de Magisk:** instala IR Face como app privilegiada del sistema, le da el permiso de
  agente de confianza, hace visible la cámara 5 y concede la cámara en cada arranque.
  Sobrevive a las actualizaciones de LineageOS junto con Magisk.

---

## 1. Compilar

Los modelos ya deben estar en `app/src/main/assets` (de la fase 1).

Android Studio: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.

## 2. Crear el módulo

```bash
./make_module.sh        # genera IrFace-magisk.zip  (necesita zip/unzip)
```

## 3. Instalar

```bash
adb uninstall org.irface                 # quita el prototipo (habrá que registrar la cara otra vez)
adb push IrFace-magisk.zip /sdcard/Download/
```

En el teléfono: **Magisk → Módulos → Instalar desde almacenamiento →** `IrFace-magisk.zip`
→ **Reiniciar**.

## 4. Configurar (una vez)

1. Abre **IR Face**. Magisk pedirá superusuario: **Conceder** (para siempre).
2. **Registrar** (con gafas) y otra vez **Registrar** (sin gafas). Comprueba con **Verificar**.
   El umbral ya viene en 0.60.
3. Pulsa **Agentes de confianza** y activa **IR Face**.
   (También en Ajustes → Seguridad y privacidad → Más ajustes de seguridad → Agentes de confianza.)
4. Bloquea, desbloquea **una vez con PIN o huella**, y vuelve a bloquear.
5. Enciende la pantalla y mira al teléfono → debería desbloquearse.

## Opciones de la pantalla de bloqueo (en la app)

- **Deslizar para entrar tras reconocerme** (activado por defecto): al reconocerte, el candado
  se abre pero la pantalla de bloqueo se queda hasta que deslizas, como en MIUI.
  Desactívalo para entrar directamente.
- **Mensajes en la pantalla de bloqueo**: "Cara no reconocida" / "Cara reconocida · desliza para entrar".
- **Carita animada (estilo MIUI)**: busca → niega con la cabeza si no te reconoce → tick si te
  reconoce. **Toca la carita para reintentar.** Se dibuja con un servicio de accesibilidad
  (es la única forma de dibujar encima de la pantalla de bloqueo); no lee la pantalla.
  Algunas apps de banca avisan cuando hay servicios de accesibilidad activos: si te molesta,
  desactiva solo la carita.
- **Probar carita / Carita ↑ ↓**: vista previa y ajuste de la posición.

## Actualizar a una versión nueva

Compila, ejecuta `./make_module.sh` y vuelve a instalar el módulo (reemplaza al anterior;
tu cara registrada y tus ajustes se conservan):

```bash
adb push IrFace-magisk.zip /sdcard/Download/
adb root
adb shell magisk --install-module /sdcard/Download/IrFace-magisk.zip
adb reboot
```

**No instales el APK suelto con `adb install`**: el módulo trae también los permisos que necesita.

## Diagnóstico

```bash
adb logcat -s IRFACE TrustManagerService TrustAgentWrapper
```

## Si algo va mal

- Desactiva el módulo en Magisk → Módulos y reinicia.
- Si el teléfono no arrancara: conéctalo y ejecuta
  `adb wait-for-device shell magisk --remove-modules` (desactiva todos los módulos y reinicia).
