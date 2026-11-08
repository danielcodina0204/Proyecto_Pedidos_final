package pedidos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos que envía el cliente para crear o actualizar un pedido.
 * El estado es opcional: al crear se usa "pendiente" y al actualizar se conserva el actual.
 */
public record DatosPedido(
        @NotBlank @Size(max = 100) String cliente,
        @NotBlank @Size(max = 100) String producto,
        @NotNull @Min(1) @Max(10000) Integer cantidad,
        @Pattern(regexp = DatosPedido.ESTADOS) String estado) {

    static final String ESTADOS = "pendiente|en proceso|enviado|entregado";
}
