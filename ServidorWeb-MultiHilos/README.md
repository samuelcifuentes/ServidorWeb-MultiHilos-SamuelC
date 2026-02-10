# Servidor Web Multi-hilos con Galeria de Imagenes

La pagina es una galeria de imagenes que corre en un servidor web multi-hilos implementado en Java. El servidor acepta conexiones concurrentes en puerto 8080, interpreta solicitudes HTTP/1.0 y HTTP/1.1, y permite a los usuarios subir imagenes JPG, JPEG, GIF y PNG mediante un formulario de carga. Las imagenes se guardan en la carpeta /uploads y se muestran en una galeria donde se pueden ver en tamaño completo al hacer clic. El servidor maneja multiples conexiones simultaneas usando un hilo independiente para cada cliente, cierra correctamente todos los recursos del sistema, valida todas las rutas para evitar acceso no autorizado, y registra en consola todos los detalles de las solicitudes HTTP.

## Como Usar

Compilar:
javac ServidorWebMultihilo.java
Ejecutar con puerto por defecto:
java ServidorWebMultihilo
Ejecutar con puerto personalizado:
java ServidorWebMultihilo 9000
Luego abrir el navegador en http://localhost:8080/

## Estructura

- ServidorWebMultihilo.java - Codigo fuente del servidor
- index.html - Interfaz de galeria
- uploads/ - Directorio donde se guardan las imagenes

## Funcionalidades

- Maneja multiples usuarios simultaneos
- Valida que las rutas no salgan del directorio base
- Soporta subida de imagenes JPG, JPEG, GIF y PNG
- Visualiza imagenes en galeria con preview al hacer clic
- Autorefresh cada 5 segundos
- Registro completo de todas las solicitudes HTTP en consola
