package pedidos;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Pruebas unitarias de las reglas de validación de un pedido. */
class DatosPedidoTest {

    private static final Validator VALIDADOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static boolean esValido(DatosPedido datos) {
        return VALIDADOR.validate(datos).isEmpty();
    }

    @Test
    void aceptaUnPedidoSinEstado() {
        assertTrue(esValido(new DatosPedido("Ana Pérez", "Teclado", 3, null)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"pendiente", "en proceso", "enviado", "entregado"})
    void aceptaLosCuatroEstados(String estado) {
        assertTrue(esValido(new DatosPedido("Ana Pérez", "Teclado", 3, estado)));
    }

    @Test
    void rechazaUnEstadoDesconocido() {
        assertFalse(esValido(new DatosPedido("Ana Pérez", "Teclado", 3, "cancelado")));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10001})
    void rechazaCantidadesFueraDeRango(int cantidad) {
        assertFalse(esValido(new DatosPedido("Ana Pérez", "Teclado", cantidad, null)));
    }

    @Test
    void rechazaDatosObligatoriosVaciosOAusentes() {
        assertFalse(esValido(new DatosPedido("   ", "Teclado", 3, null)));
        assertFalse(esValido(new DatosPedido("Ana Pérez", null, 3, null)));
        assertFalse(esValido(new DatosPedido("Ana Pérez", "Teclado", null, null)));
    }

    @Test
    void rechazaTextosDeMasDeCienCaracteres() {
        assertFalse(esValido(new DatosPedido("Ana Pérez", "x".repeat(101), 3, null)));
    }

    @Test
    void elCambioDeEstadoExigeUnEstadoPermitido() {
        assertTrue(VALIDADOR.validate(new CambioEstado("enviado")).isEmpty());
        assertFalse(VALIDADOR.validate(new CambioEstado(null)).isEmpty());
        assertFalse(VALIDADOR.validate(new CambioEstado("cancelado")).isEmpty());
    }
}
