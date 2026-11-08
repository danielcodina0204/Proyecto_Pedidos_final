package pedidos;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pedidos.PedidoRepositorio.PrimariaNoDisponible;

/** API REST de gestión de pedidos. Cada nodo backend ejecuta esta misma aplicación. */
@RestController
@RequestMapping("/api")
class PedidoControlador {

    private final PedidoRepositorio repositorio;
    private final String nodo;

    PedidoControlador(PedidoRepositorio repositorio, @Value("${NODO_ID}") String nodo) {
        this.repositorio = repositorio;
        this.nodo = nodo;
    }

    @GetMapping("/health")
    Map<String, Object> salud() {
        return Map.of("nodo", nodo, "primaria", repositorio.primariaDisponible(),
                "replica", repositorio.replicaDisponible());
    }

    @GetMapping("/pedidos")
    List<Pedido> listar() {
        return repositorio.listar();
    }

    @PostMapping("/pedidos")
    @ResponseStatus(HttpStatus.CREATED)
    Pedido crear(@Valid @RequestBody DatosPedido datos) {
        return repositorio.crear(datos);
    }

    @PutMapping("/pedidos/{id}")
    Pedido actualizar(@PathVariable int id, @Valid @RequestBody DatosPedido datos) {
        return repositorio.actualizar(id, datos).orElseThrow(PedidoControlador::noEncontrado);
    }

    @PutMapping("/pedidos/{id}/estado")
    Pedido cambiarEstado(@PathVariable int id, @Valid @RequestBody CambioEstado cambio) {
        return repositorio.cambiarEstado(id, cambio.estado()).orElseThrow(PedidoControlador::noEncontrado);
    }

    @DeleteMapping("/pedidos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void eliminar(@PathVariable int id) {
        if (!repositorio.eliminar(id)) {
            throw noEncontrado();
        }
    }

    @ExceptionHandler(PrimariaNoDisponible.class)
    ProblemDetail sinPrimaria() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Base de datos primaria no disponible: modo solo lectura");
    }

    @ExceptionHandler(DataAccessResourceFailureException.class)
    ProblemDetail sinBaseDeDatos() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Base de datos no disponible");
    }

    private static ResponseStatusException noEncontrado() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado");
    }
}
