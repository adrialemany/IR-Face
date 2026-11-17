#!/system/bin/sh
# La cámara de infrarrojos (id 5) solo es visible para las apps de esta lista (máx. 91 caracteres)
resetprop -n vendor.camera.aux.packagelist "com.android.camera,org.lineageos.aperture,org.irface"
# Obliga a Android a releer IR Face en cada arranque (si no, tras actualizar puede seguir viendo la versión antigua)
rm -rf /data/system/package_cache/*/IrFace* 2>/dev/null
