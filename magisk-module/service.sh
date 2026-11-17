#!/system/bin/sh
# Se ejecuta en cada arranque, cuando Android ya está en marcha
until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 2; done
sleep 5
# Por si algún script del sistema restableció la lista al arrancar
resetprop vendor.camera.aux.packagelist "com.android.camera,org.lineageos.aperture,org.irface"
# Permiso de cámara y acceso a la cámara aunque la app esté en segundo plano
pm grant org.irface android.permission.CAMERA 2>/dev/null
appops set org.irface CAMERA allow 2>/dev/null
# El emisor IR siempre arranca apagado
echo 0 > /sys/class/leds/ir_torch/brightness 2>/dev/null
