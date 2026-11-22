# Motor v14 — versión final

- Organiza los recursos en **Assets comunes** (importación manual) y **Assets especiales** (ZIP con PNG recortados y coordenadas del lienzo original).
- Corrige el hit testing alfa: solo alfa exactamente cero es transparente; el alfa original del recurso se evalúa independientemente de `modulate` y la opacidad del nodo.
- Ajusta el tamaño visible, los tiradores del editor y la vista previa de coordenadas a las órdenes de recorte.
- Añade secciones anidadas «Al terminar» a la reproducción de audio, video y animación, además de los cambios progresivos.
- Añade deshacer y rehacer para movimiento, rotación, tamaño, creación y eliminación de nodos.
- Elimina el trazo adicional alrededor del viewport en el editor.
- Incluye `motor-v14.zip` con el APK, la herramienta HTML sin conexión y la documentación.

El ZIP de recorte descargado desde la herramienta HTML es distinto al paquete de publicación. Impórtalo con el botón **Assets → Importar ZIP especial…**; los PNG aparecerán en la sección **Assets especiales**.
