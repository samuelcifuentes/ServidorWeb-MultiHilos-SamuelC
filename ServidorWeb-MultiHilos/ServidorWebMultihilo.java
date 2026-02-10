import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.StringTokenizer;


public class ServidorWebMultihilo {

    public static void main(String[] args) throws IOException {

        int puerto = 8080;

        if (args.length > 0) {
            try {
                puerto = Integer.parseInt(args[0]);
                if (puerto <= 1024) {
                    System.err.println("Error: El puerto debe ser mayor a 1024. Puerto proporcionado: " + puerto);
                    return;
                }
            } catch (NumberFormatException e) {
                System.err.println("Error: Puerto inválido. Usando puerto 8080 por defecto.");
                puerto = 8080;
            }
        }

        ServerSocket servidor = null;
        try {
            servidor = new ServerSocket(puerto);
            System.out.println(">> Servidor Web Multi-hilos iniciado");
            System.out.println(">> Escuchando en puerto: " + puerto);
            System.out.println(">> Protocolo: HTTP/1.0 y HTTP/1.1");
            System.out.println(">> Metodo soportado: GET y POST");

            while (true) {
                Socket socket = servidor.accept();
                System.out.println("[CONEXIÓN] Cliente conectado: " + socket.getInetAddress().getHostAddress());
                new Thread(new ClienteHandler(socket)).start();
            }
        } catch (Exception e) {
            System.err.println("Error en servidor: " + e.getMessage());
        } finally {
            if (servidor != null && !servidor.isClosed()) {
                try {
                    servidor.close();
                } catch (IOException e) {
                    System.err.println("Error cerrando servidor: " + e.getMessage());
                }
            }
        }
    }
}

class ClienteHandler implements Runnable {

    private Socket socket;
    private static final String CRLF = "\r\n";
    private static final String DIRECTORIO_BASE = ".";
    private static final String ARCHIVO_INDICE = "index.html";

    public ClienteHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        BufferedReader in = null;
        OutputStream out = null;

        try {
            in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = socket.getOutputStream();

            String requestLine = in.readLine();
            if (requestLine == null || requestLine.trim().isEmpty()) {
                enviar400(out);
                return;
            }

            System.out.println("\n--- SOLICITUD HTTP RECIBIDA ---");
            System.out.println("│ Linea de solicitud:");
            System.out.println("│   " + requestLine);

            String[] solicitud = requestLine.split("\\s+");

            if (solicitud.length < 3) {
                System.out.println("│ -> Error: Formato de solicitud invalido");
                enviar400(out);
                return;
            }

            String metodo = solicitud[0].toUpperCase();
            String recurso = solicitud[1];
            String version = solicitud[2].toUpperCase();

            if (!version.equals("HTTP/1.0") && !version.equals("HTTP/1.1")) {
                System.out.println("│ -> Error: Solo se soportan HTTP/1.0 y HTTP/1.1, recibido: " + version);
                enviar505(out);
                return;
            }

            if (!metodo.equals("GET") && !metodo.equals("POST")) {
                System.out.println("│ -> Error: Solo GET y POST estan soportados, recibido: " + metodo);
                enviar501(out);
                return;
            }

            System.out.println("│ Encabezados HTTP:");
            String header;
            int contentLength = 0;
            String contentType = "";
            
            while ((header = in.readLine()) != null && !header.isEmpty()) {
                System.out.println("│   " + header);
                
                if (header.startsWith("Content-Length:"))
                    contentLength = Integer.parseInt(header.split(":")[1].trim());
                
                if (header.startsWith("Content-Type:"))
                    contentType = header.split(": ")[1];
            }

            System.out.println("└─────────────────────────────────────────┘");

            if (metodo.equals("GET")) {
                manejarGET(recurso, out);
            } else if (metodo.equals("POST")) {
                manejarPOST(in, contentLength, contentType, out);
            }
            out.flush();

        } catch (IOException e) {
            System.err.println("Error en comunicacion: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Error procesando solicitud: " + e.getMessage());
        } finally {
            cerrarRecursos(in, out);
        }
    }


    private void manejarPOST(BufferedReader in, int contentLength,
                             String contentType, OutputStream out) throws Exception {

        if (!contentType.contains("multipart/form-data")) {
            String body = "<html><body><h1>400 Bad Request</h1></body></html>";
            String response =
                    "HTTP/1.1 400 Bad Request" + CRLF +
                    "Content-Type: text/html" + CRLF +
                    "Content-Length: " + body.length() + CRLF +
                    "Connection: close" + CRLF +
                    CRLF + body;
            out.write(response.getBytes(StandardCharsets.UTF_8));
            return;
        }

        char[] bodyChars = new char[contentLength];
        int totalRead = in.read(bodyChars, 0, contentLength);
        
        String data = new String(bodyChars, 0, totalRead);
        System.out.println(">>> Recibido " + totalRead + " bytes");
        
        String[] boundaryParts = contentType.split("boundary=");
        if (boundaryParts.length < 2) {
            System.out.println("!!! No se encuentra boundary en Content-Type");
            return;
        }
        String boundary = boundaryParts[1];
        
        String[] parts = data.split("--" + boundary);
        System.out.println(">>> Encontradas " + parts.length + " partes en el multipart");

        for (String part : parts) {
            if (part.contains("filename=")) {
                try {
                    String fileName = part.split("filename=\"")[1].split("\"")[0];
                    System.out.println(">>> Procesando archivo: " + fileName);

                    String lowerName = fileName.toLowerCase();
                    if (!(lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || 
                          lowerName.endsWith(".gif") || lowerName.endsWith(".png"))) {
                        System.out.println("* Archivo rechazado (tipo no permitido): " + fileName);
                        continue;
                    }

                    int headerEnd = part.indexOf("\r\n\r\n");
                    if (headerEnd == -1) {
                        System.out.println("!!! No se encuentra fin de headers para: " + fileName);
                        continue;
                    }
                    
                    int contentStart = headerEnd + 4;
                    int contentEnd = part.lastIndexOf("\r\n");
                    if (contentEnd <= contentStart) {
                        System.out.println("!!! Contenido vacio para: " + fileName);
                        continue;
                    }
                    
                    String fileContent = part.substring(contentStart, contentEnd);
                    System.out.println(">>> Tamaño del contenido: " + fileContent.length() + " caracteres");

                    File uploadsDir = new File("./uploads");
                    if (!uploadsDir.exists()) {
                        uploadsDir.mkdir();
                    }

                    FileOutputStream fos = new FileOutputStream("./uploads/" + fileName);
                    fos.write(fileContent.getBytes(StandardCharsets.ISO_8859_1));
                    fos.close();

                    System.out.println("[OK] Archivo guardado: " + fileName);
                } catch (Exception e) {
                    System.err.println("!!! Error guardando archivo: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        String responseBody = "<html><head><title>Upload Success</title></head>" +
                "<body><h2>Imagen subida correctamente</h2>" +
                "<p><a href='/'>Volver</a></p></body></html>";

        String response =
                "HTTP/1.1 200 OK" + CRLF +
                "Content-Type: text/html" + CRLF +
                "Content-Length: " + responseBody.length() + CRLF +
                "Connection: close" + CRLF +
                CRLF +
                responseBody;

        out.write(response.getBytes(StandardCharsets.UTF_8));
        System.out.println(">>> [200 OK] Respuesta POST enviada");
    }

    
    private void manejarGET(String recurso, OutputStream out) throws IOException {
        if (recurso.equals("/api/images")) {
            manejarAPIImagenes(out);
            return;
        }

        String fileName = recurso;
        
        if (fileName.startsWith("/")) {
            fileName = fileName.substring(1);
        }
        
        if (fileName.isEmpty() || fileName.equals("/")) {
            fileName = ARCHIVO_INDICE;
        }

        File file = new File(DIRECTORIO_BASE, fileName);
        String rutaCanonica = file.getCanonicalPath();
        String baseCanonica = new File(DIRECTORIO_BASE).getCanonicalPath();

        if (!rutaCanonica.startsWith(baseCanonica)) {
            System.out.println("⚠ Intento de acceso fuera del directorio permitido: " + fileName);
            enviar404(out);
            return;
        }

        if (file.exists() && file.isFile()) {
            try {
                String mimeType = obtenerMimeType(fileName);
                byte[] contenido = Files.readAllBytes(file.toPath());

                String response =
                        "HTTP/1.1 200 OK" + CRLF +
                        "Content-Type: " + mimeType + CRLF +
                        "Content-Length: " + contenido.length + CRLF +
                        "Connection: close" + CRLF +
                        CRLF;

                out.write(response.getBytes(StandardCharsets.UTF_8));
                out.write(contenido);

                System.out.println("✓ [200 OK] Recurso servido: " + fileName + " (" + mimeType + ")");
            } catch (FileNotFoundException e) {
                System.out.println("✗ [404] Recurso no encontrado: " + fileName);
                enviar404(out);
            }
        } else {
            System.out.println("✗ [404] Recurso no encontrado: " + fileName);
            enviar404(out);
        }
    }

   
    private void manejarAPIImagenes(OutputStream out) throws IOException {
        File uploadsDir = new File("./uploads");
        StringBuilder jsonArray = new StringBuilder("[");

        if (uploadsDir.exists() && uploadsDir.isDirectory()) {
            File[] archivos = uploadsDir.listFiles((dir, name) ->
                    name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                    name.endsWith(".gif") || name.endsWith(".png"));

            if (archivos != null && archivos.length > 0) {
                for (int i = 0; i < archivos.length; i++) {
                    jsonArray.append("\"").append(archivos[i].getName()).append("\"");
                    if (i < archivos.length - 1) {
                        jsonArray.append(",");
                    }
                }
            }
        }

        jsonArray.append("]");
        byte[] respuestaJSON = jsonArray.toString().getBytes(StandardCharsets.UTF_8);

        String response =
                "HTTP/1.1 200 OK" + CRLF +
                "Content-Type: application/json" + CRLF +
                "Content-Length: " + respuestaJSON.length + CRLF +
                "Connection: close" + CRLF +
                CRLF;

        out.write(response.getBytes(StandardCharsets.UTF_8));
        out.write(respuestaJSON);
    }


    private void enviar400(OutputStream out) throws IOException {
        String body = "<html><head><title>400 Bad Request</title></head>" +
                "<body><h1>400 Bad Request</h1><p>La solicitud es inválida.</p></body></html>";

        String response =
                "HTTP/1.0 400 Bad Request" + CRLF +
                "Content-Type: text/html" + CRLF +
                "Content-Length: " + body.length() + CRLF +
                "Connection: close" + CRLF +
                CRLF +
                body;

        out.write(response.getBytes(StandardCharsets.UTF_8));
        System.out.println("✗ [400 Bad Request] Solicitud inválida");
    }

    /**
     * Envía respuesta 404 Not Found
     */
    private void enviar404(OutputStream out) throws IOException {
        String body = "<html><head><title>404 Not Found</title></head>" +
                "<body><h1>404 - Recurso No Encontrado</h1>" +
                "<p>El recurso solicitado no existe en el servidor.</p>" +
                "<hr><p><small>Servidor Web Multi-hilos HTTP/1.0</small></p>" +
                "</body></html>";

        String response =
                "HTTP/1.0 404 Not Found" + CRLF +
                "Content-Type: text/html; charset=utf-8" + CRLF +
                "Content-Length: " + body.length() + CRLF +
                "Connection: close" + CRLF +
                CRLF +
                body;

        out.write(response.getBytes(StandardCharsets.UTF_8));
    }


    private void enviar501(OutputStream out) throws IOException {
        String body = "<html><head><title>501 Not Implemented</title></head>" +
                "<body><h1>501 - Método No Implementado</h1>" +
                "<p>Solo GET y POST están soportados.</p></body></html>";

        String response =
                "HTTP/1.0 501 Not Implemented" + CRLF +
                "Content-Type: text/html" + CRLF +
                "Content-Length: " + body.length() + CRLF +
                "Connection: close" + CRLF +
                CRLF +
                body;

        out.write(response.getBytes(StandardCharsets.UTF_8));
    }


    private void enviar505(OutputStream out) throws IOException {
        String body = "<html><head><title>505 HTTP Version Not Supported</title></head>" +
                "<body><h1>505 - Versión HTTP No Soportada</h1>" +
                "<p>Solo se soportan HTTP/1.0 y HTTP/1.1.</p></body></html>";

        String response =
                "HTTP/1.1 505 HTTP Version Not Supported" + CRLF +
                "Content-Type: text/html" + CRLF +
                "Content-Length: " + body.length() + CRLF +
                "Connection: close" + CRLF +
                CRLF +
                body;

        out.write(response.getBytes(StandardCharsets.UTF_8));
    }


    private String obtenerMimeType(String nombreArchivo) {
        if (nombreArchivo.endsWith(".html") || nombreArchivo.endsWith(".htm")) {
            return "text/html; charset=utf-8";
        } else if (nombreArchivo.endsWith(".jpg") || nombreArchivo.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (nombreArchivo.endsWith(".gif")) {
            return "image/gif";
        } else if (nombreArchivo.endsWith(".png")) {
            return "image/png";
        } else if (nombreArchivo.endsWith(".css")) {
            return "text/css";
        } else if (nombreArchivo.endsWith(".js")) {
            return "application/javascript";
        } else if (nombreArchivo.endsWith(".txt")) {
            return "text/plain";
        }
        return "application/octet-stream";
    }


    private void cerrarRecursos(BufferedReader in, OutputStream out) {
        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException e) {
            System.err.println("Error cerrando BufferedReader: " + e.getMessage());
        }

        try {
            if (out != null) {
                out.close();
            }
        } catch (IOException e) {
            System.err.println("Error cerrando OutputStream: " + e.getMessage());
        }

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Error cerrando socket: " + e.getMessage());
        }

        System.out.println("[DESCONEXIÓN] Socket cerrado correctamente");
    }
}
