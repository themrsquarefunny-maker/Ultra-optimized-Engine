# Motor v13

- Funciones `void` o con valor, argumentos opcionales con valores por defecto y llamadas con formulario adaptado a la firma.
- Zoom y desplazamiento solo en el editor: al alejar la vista se muestran los nodos fuera del viewport.
- El recorte de bordes se hace externamente en `trim-assets.html`. **Assets → Importar órdenes** restaura el desplazamiento de los PNG ya recortados, sin escanear bordes en el motor.
- `motor-v13.zip` contiene el APK, la herramienta HTML sin conexión y `DOCUMENTACION.txt` con todos los eventos y bloques.

El ZIP que descarga el HTML (`ordenes-assets.zip`) es distinto: contiene solo los PNG recortados y `ordenes.json`. Impórtalo desde **Assets → Importar órdenes** y asigna los nuevos recursos a los nodos. Consulta la guía para migrar proyectos antiguos.
