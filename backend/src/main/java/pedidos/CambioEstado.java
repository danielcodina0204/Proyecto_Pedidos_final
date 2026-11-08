package pedidos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Nuevo estado de un pedido; solo modifica el estado, sin tocar los demás datos. */
public record CambioEstado(@NotNull @Pattern(regexp = DatosPedido.ESTADOS) String estado) {
}
