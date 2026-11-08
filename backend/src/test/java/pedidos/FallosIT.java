package pedidos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pedidos.ClienteApi.api;
import static pedidos.ClienteApi.compose;
import static pedidos.ClienteApi.consultarReplica;
import static pedidos.ClienteApi.esperar;

import java.time.Duration;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de fallo: detienen contenedores reales y comprueban que el servicio continúa.
 * Requieren el sistema levantado con "docker compose up -d" y lo dejan nuevamente en marcha.
 */
class FallosIT {

    private static final String NUEVO = """
            {"cliente": "Prueba de fallo", "producto": "Caja", "cantidad": 1}""";
    private static final String MODO_SOLO_LECTURA = "Base de datos primaria no disponible: modo solo lectura";

    @Test
    void caidaDeUnNodoBackend() throws Exception {
        int id;
        compose("stop", "backend1");
        try {
            for (var i = 0; i < 4; i++) {
                var respuesta = api("GET", "/pedidos", null);
                assertEquals(200, respuesta.codigo());
                assertEquals("nodo-2", respuesta.nodo());
            }
            var creado = api("POST", "/pedidos", NUEVO);
            assertEquals(201, creado.codigo());
            assertEquals("nodo-2", creado.nodo());
            id = creado.cuerpo().get("id").asInt();
        } finally {
            compose("start", "backend1");
        }

        esperar(Duration.ofSeconds(60), () -> {
            for (var i = 0; i < 4; i++) {
                if ("nodo-1".equals(api("GET", "/health", null).nodo())) {
                    return true;
                }
            }
            return false;
        });
        assertEquals(204, api("DELETE", "/pedidos/" + id, null).codigo());
    }

    @Test
    void caidaDeLaBasePrimaria() throws Exception {
        var id = api("POST", "/pedidos", NUEVO).cuerpo().get("id").asInt();
        var enReplica = "SELECT count(*) FROM pedidos WHERE id = " + id;
        esperar(Duration.ofSeconds(10), () -> consultarReplica(enReplica).equals("1"));

        compose("stop", "db-primary");
        try {
            var lista = api("GET", "/pedidos", null);
            assertEquals(200, lista.codigo());
            assertTrue(StreamSupport.stream(lista.cuerpo().spliterator(), false)
                    .anyMatch(p -> p.get("id").asInt() == id));

            var error = api("POST", "/pedidos", NUEVO);
            assertEquals(503, error.codigo());
            assertEquals(MODO_SOLO_LECTURA, error.cuerpo().get("detail").asString());
            var cambio = api("PUT", "/pedidos/" + id + "/estado", """
                    {"estado": "enviado"}""");
            assertEquals(503, cambio.codigo());
            assertEquals(MODO_SOLO_LECTURA, cambio.cuerpo().get("detail").asString());

            var salud = api("GET", "/health", null).cuerpo();
            assertFalse(salud.get("primaria").asBoolean());
            assertTrue(salud.get("replica").asBoolean());
        } finally {
            compose("start", "db-primary");
        }

        esperar(Duration.ofSeconds(60), () -> api("DELETE", "/pedidos/" + id, null).codigo() == 204);
        esperar(Duration.ofSeconds(30), () -> consultarReplica(enReplica).equals("0"));
    }
}
