package pedidos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pedidos.ClienteApi.api;
import static pedidos.ClienteApi.consultarReplica;
import static pedidos.ClienteApi.esperar;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

/** Pruebas de integración: requieren el sistema levantado con "docker compose up -d". */
class IntegracionIT {

    private static final String NUEVO = """
            {"cliente": "Ana Pérez", "producto": "Teclado", "cantidad": 2}""";

    @Test
    void cicloCompletoDeUnPedido() throws Exception {
        var creado = api("POST", "/pedidos", NUEVO);
        assertEquals(201, creado.codigo());
        assertEquals("pendiente", creado.cuerpo().get("estado").asString());
        var id = creado.cuerpo().get("id").asInt();
        var ruta = "/pedidos/" + id;

        var actualizado = api("PUT", ruta, """
                {"cliente": "Ana Pérez", "producto": "Teclado", "cantidad": 5, "estado": "en proceso"}""");
        assertEquals(200, actualizado.codigo());
        assertEquals(5, actualizado.cuerpo().get("cantidad").asInt());
        assertEquals("en proceso", actualizado.cuerpo().get("estado").asString());

        var editado = api("PUT", ruta, """
                {"cliente": "Ana María Pérez", "producto": "Teclado", "cantidad": 5}""");
        assertEquals("Ana María Pérez", editado.cuerpo().get("cliente").asString());
        assertEquals("en proceso", editado.cuerpo().get("estado").asString(), "Sin estado se conserva el actual");

        var lista = api("GET", "/pedidos", null).cuerpo();
        assertTrue(StreamSupport.stream(lista.spliterator(), false).anyMatch(p -> p.get("id").asInt() == id));

        assertEquals(204, api("DELETE", ruta, null).codigo());
        assertEquals(404, api("DELETE", ruta, null).codigo());
        assertEquals(404, api("PUT", ruta, NUEVO).codigo());
    }

    @Test
    void cambiarElEstadoConservaLosDemasDatos() throws Exception {
        var ruta = "/pedidos/" + api("POST", "/pedidos", NUEVO).cuerpo().get("id").asInt();
        api("PUT", ruta, """
                {"cliente": "Ana Pérez", "producto": "Teclado", "cantidad": 5}""");

        var cambiado = api("PUT", ruta + "/estado", """
                {"estado": "enviado"}""");
        assertEquals(200, cambiado.codigo());
        assertEquals("enviado", cambiado.cuerpo().get("estado").asString());
        assertEquals(5, cambiado.cuerpo().get("cantidad").asInt(), "No se sobrescribe la cantidad editada");
        assertEquals(400, api("PUT", ruta + "/estado", """
                {"estado": "cancelado"}""").codigo());

        assertEquals(204, api("DELETE", ruta, null).codigo());
        assertEquals(404, api("PUT", ruta + "/estado", """
                {"estado": "enviado"}""").codigo());
    }

    @Test
    void datosInvalidosDevuelven400() throws Exception {
        assertEquals(400, api("POST", "/pedidos", """
                {"cliente": "Ana", "producto": "Teclado", "cantidad": 0}""").codigo());
        assertEquals(400, api("POST", "/pedidos", """
                {"cliente": "Ana", "producto": "Teclado", "cantidad": 2.5}""").codigo());
        assertEquals(400, api("POST", "/pedidos", """
                {"cliente": "Ana", "producto": "Teclado", "cantidad": 1, "estado": "cancelado"}""").codigo());
        assertEquals(400, api("POST", "/pedidos", """
                {"id": 1, "cliente": "Ana", "producto": "Teclado", "cantidad": 1}""").codigo());
        assertEquals(400, api("POST", "/pedidos", "{\"cliente\":").codigo());
        assertEquals(400, api("PUT", "/pedidos/99999999999", NUEVO).codigo());
    }

    @Test
    void elBalanceadorRepartePorLosDosNodos() throws Exception {
        Set<String> nodos = new HashSet<>();
        for (var i = 0; i < 6; i++) {
            nodos.add(api("GET", "/health", null).nodo());
        }
        assertEquals(Set.of("nodo-1", "nodo-2"), nodos);
    }

    @Test
    void healthInformaLasDosBasesDisponibles() throws Exception {
        var salud = api("GET", "/health", null);
        assertEquals(200, salud.codigo());
        assertTrue(salud.cuerpo().get("primaria").asBoolean());
        assertTrue(salud.cuerpo().get("replica").asBoolean());
    }

    @Test
    void losCambiosSeReplicanEnLaReplica() throws Exception {
        var id = api("POST", "/pedidos", NUEVO).cuerpo().get("id").asInt();
        var enReplica = "SELECT count(*) FROM pedidos WHERE id = " + id;
        esperar(Duration.ofSeconds(10), () -> consultarReplica(enReplica).equals("1"));
        api("DELETE", "/pedidos/" + id, null);
        esperar(Duration.ofSeconds(10), () -> consultarReplica(enReplica).equals("0"));
    }
}
