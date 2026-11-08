package pedidos;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Utilidades para probar el sistema desplegado con Docker Compose a través de NGINX. */
final class ClienteApi {

    private static final String URL_API = "http://localhost:8080/api";
    private static final Path RAIZ_PROYECTO = Path.of("..").toAbsolutePath().normalize();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    record Respuesta(int codigo, String nodo, JsonNode cuerpo) {
    }

    @FunctionalInterface
    interface Condicion {
        boolean cumple() throws Exception;
    }

    private ClienteApi() {
    }

    static Respuesta api(String metodo, String ruta, String json) throws Exception {
        var peticion = HttpRequest.newBuilder(URI.create(URL_API + ruta))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .method(metodo, json == null ? BodyPublishers.noBody() : BodyPublishers.ofString(json))
                .build();
        var respuesta = HTTP.send(peticion, BodyHandlers.ofString());
        var cuerpo = respuesta.body().isEmpty() ? null : JSON.readTree(respuesta.body());
        return new Respuesta(respuesta.statusCode(), respuesta.headers().firstValue("X-Node").orElse(null), cuerpo);
    }

    static String compose(String... argumentos) throws Exception {
        var comando = new ArrayList<>(List.of("docker", "compose"));
        comando.addAll(List.of(argumentos));
        var proceso = new ProcessBuilder(comando).directory(RAIZ_PROYECTO.toFile())
                .redirectError(ProcessBuilder.Redirect.INHERIT).start();
        var salida = new String(proceso.getInputStream().readAllBytes()).strip();
        if (proceso.waitFor() != 0) {
            throw new IllegalStateException("Falló: " + String.join(" ", comando));
        }
        return salida;
    }

    static String consultarReplica(String sql) throws Exception {
        return compose("exec", "-T", "db-replica", "psql", "-U", "admin", "-d", "pedidos", "-tAc", sql);
    }

    static void esperar(Duration limite, Condicion condicion) throws Exception {
        var fin = Instant.now().plus(limite);
        while (Instant.now().isBefore(fin)) {
            if (condicion.cumple()) {
                return;
            }
            Thread.sleep(1000);
        }
        throw new AssertionError("La condición no se cumplió en " + limite.toSeconds() + " s");
    }
}
