# IR Face

Real **infrared** face unlock for the **Xiaomi Mi 8 (dipper)** running LineageOS / AOSP
custom ROMs, where the stock MIUI face unlock is gone.

It uses the phone's dedicated IR camera and IR emitter — the same hardware MIUI used — so
it works in complete darkness and is much harder to fool with a photo than a selfie-camera
unlock. No need to recompile the ROM: everything ships as a Magisk module.

> **Heads up:** this is a *weak* biometric, just like the original Mi 8 face unlock. It
> unlocks the lock screen, but Android still asks for your PIN/fingerprint after a reboot
> and after its security timeouts, and banking/payment apps that require strong biometrics
> won't use it.

## How it works

- **Hardware** — camera id `5` (OV7251 IR sensor) + the `/sys/class/leds/ir_torch` emitter.
- **Recognition** — [YuNet](https://github.com/opencv/opencv_zoo/tree/main/models/face_detection_yunet)
  for detection and [SFace](https://github.com/opencv/opencv_zoo/tree/main/models/face_recognition_sface)
  for recognition, both running fully on-device via OpenCV. Your face is stored locally as a
  set of embeddings; no image ever leaves the phone.
- **Unlocking** — IR Face is a **trust agent** using Android's *Active Unlock* (API 13+),
  the same mechanism Google uses for watch-unlock. When the screen turns on while locked, it
  briefly fires the IR emitter, recognizes your face and dismisses the keyguard.
- **Magisk module** — installs IR Face as a privileged system app, grants it the trust-agent
  permission, makes the IR camera visible and re-applies everything on every boot. It
  survives LineageOS updates alongside Magisk.

## Features

- Real IR face unlock (works in the dark).
- MIUI-style animated lock-screen icon: searching → shakes "no" / check mark on success.
  Tap it to retry. Optional, size and position adjustable.
- "Swipe to enter after recognition" (MIUI-style) or instant unlock.
- Localized UI and lock-screen messages: English, Spanish, Catalan (follows system language).
- Adjustable recognition threshold; enrol multiple looks (e.g. with and without glasses).

## Requirements

- Xiaomi Mi 8 (**dipper**) — see *Porting* below for other devices.
- A custom ROM that exposes the IR camera (LineageOS 22 / Android 15 tested).
- **Magisk** (root).
- Android Studio (or the Android SDK) to build the APK.

## Build

1. Build the APK (Android Studio: *Build → Build APK(s)*, or from the terminal):
   ```bash
   export ANDROID_HOME=$HOME/Android/Sdk
   ./gradlew assembleDebug
   ```

## Install

```bash
./make_module.sh                       # bundles the APK into IrFace-magisk.zip
adb push IrFace-magisk.zip /sdcard/Download/
adb shell su -c "magisk --install-module /sdcard/Download/IrFace-magisk.zip"
adb reboot
```

Then:

1. Open **IR Face**, grant superuser when Magisk asks.
2. **Enrol** your face (do it once with glasses and once without).
3. Tap **Trust agents** and enable **IR Face**.
4. Unlock once with PIN/fingerprint, lock the phone, then turn the screen on.

## Configuration (device-specific values)

These constants are specific to the Mi 8. To adapt to another device, change:

| What | Where |
|------|-------|
| IR camera id (`5`) | `Store.CAMERA_ID` |
| IR emitter path (`/sys/class/leds/ir_torch`) | `IrLed.LED_PATH`, and `post-fs-data.sh` / `service.sh` |
| Camera allow-list property | `magisk-module/post-fs-data.sh` |
| Recognition threshold | in-app, or `Store.DEFAULT_THRESHOLD` |

Find your device's IR camera and emitter with:

```bash
adb shell dumpsys media.camera | grep -iE "Facing|pixelArraySize"
adb shell ls /sys/class/leds
```

## Porting to other devices

- **Plug-and-play:** other Mi 8 (dipper) units on LineageOS / AOSP ROMs.
- **With the edits above:** other phones that still expose a front IR camera + emitter to
  AOSP. Many devices hide this hardware from AOSP, in which case it can't work.
- **As a template:** the Active-Unlock trust-agent + OpenCV approach also works with the
  normal front camera (2D), if you only want selfie-camera face unlock on AOSP.

## Security

This is a convenience unlock, not a strong biometric. It does not use the device's secure
enclave (only the OEM can), so Android treats it as weak: PIN/fingerprint is still required
after reboot and periodic timeouts, and it can't authorize payments. IR makes spoofing with
a photo on a screen much harder (screens barely reflect IR), but it is not foolproof.

## Credits & license

- Face models from the [OpenCV Zoo](https://github.com/opencv/opencv_zoo):
  YuNet (MIT), SFace (Apache-2.0).
- [OpenCV](https://opencv.org/) (Apache-2.0).
- Packaging via [Magisk](https://github.com/topjohnwu/Magisk).

Project code: see [LICENSE](LICENSE).
