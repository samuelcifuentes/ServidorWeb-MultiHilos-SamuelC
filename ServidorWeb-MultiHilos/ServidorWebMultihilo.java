import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.StringTokenizer;

public class ServidorWebMultihilo {

    public static void main(String[] args) throws IOException {

        int puerto = 8080;

        if (args.length > 0) {
            puerto = Integer.parseInt(args[0]);
            if (puerto <= 1024) {
                System.out.println("El puerto debe ser mayor a 1024");
                return;
            }
        }

        ServerSocket servidor = new ServerSocket(puerto);
        System.out.println("Servidor iniciado en puerto " + puerto);

        while (true) {
            Socket socket = servidor.accept();
            new Thread(new ClienteHandler(socket)).start();
        }
    }
}

class ClienteHandler implements Runnable {

    private Socket socket;
    private static final String CRLF = "\r\n";

    public ClienteHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {

        try (
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                OutputStream out = socket.getOutputStream()
        ) {

            String requestLine = in.readLine();
            if (requestLine == null) return;

            System.out.println("---- NUEVA SOLICITUD ----");
            System.out.println(requestLine);

            String header;
            int contentLength = 0;
            String contentType = "";

            while ((header = in.readLine()) != null && !header.isEmpty()) {
                System.out.println(header);

                if (header.startsWith("Content-Length:"))
                    contentLength = Integer.parseInt(header.split(":")[1].trim());

                if (header.startsWith("Content-Type:"))
                    contentType = header;
            }

            StringTokenizer tokenizer = new StringTokenizer(requestLine);
            String method = tokenizer.nextToken();
            String resource = tokenizer.nextToken();

            if (method.equals("GET")) {
                manejarGET(resource, out);
            } else if (method.equals("POST")) {
                manejarPOST(in, contentLength, contentType, out);
            }

            out.flush();

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    private void manejarGET(String resource, OutputStream out) throws IOException {

        String fileName = "." + resource;
        if (fileName.equals("./"))
            fileName = "./index.html";

        File file = new File(fileName);

        if (file.exists() && !file.isDirectory()) {

            String header =
                    "HTTP/1.0 200 OK" + CRLF +
                    "Content-Type: " + contentType(fileName) + CRLF +
                    "Content-Length: " + file.length() + CRLF +
                    CRLF;

            out.write(header.getBytes(StandardCharsets.UTF_8));
            Files.copy(file.toPath(), out);

        } else {
            enviar404(out);
        }
    }

    private void manejarPOST(BufferedReader in, int contentLength,
                             String contentType, OutputStream out) throws Exception {

        if (!contentType.contains("multipart/form-data")) {
            enviar404(out);
            return;
        }

        char[] body = new char[contentLength];
        in.read(body, 0, contentLength);
        String data = new String(body);

        String boundary = contentType.split("boundary=")[1];

        String[] parts = data.split("--" + boundary);

        for (String part : parts) {

            if (part.contains("filename=")) {

                String fileName = part.split("filename=\"")[1].split("\"")[0];

                if (!(fileName.endsWith(".jpg") || fileName.endsWith(".gif"))) {
                    continue;
                }

                String fileData = part.split("\r\n\r\n")[1];
                fileData = fileData.substring(0, fileData.lastIndexOf("\r\n"));

                FileOutputStream fos = new FileOutputStream("./uploads/" + fileName);
                fos.write(fileData.getBytes(StandardCharsets.ISO_8859_1));
                fos.close();

                System.out.println("Archivo guardado: " + fileName);
            }
        }

        String response =
                "HTTP/1.0 200 OK" + CRLF +
                "Content-Type: text/html" + CRLF +
                CRLF +
                "<html><body><h2>Imagen subida correctamente</h2>" +
                "<a href='/'>Volver</a></body></html>";

        out.write(response.getBytes(StandardCharsets.UTF_8));
    }

    private void enviar404(OutputStream out) throws IOException {

        String body = "<html><body><h1>404 Not Found</h1></body></html>";

        String response =
                "HTTP/1.0 404 Not Found" + CRLF +
                "Content-Type: text/html" + CRLF +
                "Content-Length: " + body.length() + CRLF +
                CRLF +
                body;

        out.write(response.getBytes(StandardCharsets.UTF_8));
    }

    private String contentType(String fileName) {

        if (fileName.endsWith(".html"))
            return "text/html";

        if (fileName.endsWith(".jpg"))
            return "image/jpeg";

        if (fileName.endsWith(".gif"))
            return "image/gif";

        return "application/octet-stream";
    }
}
