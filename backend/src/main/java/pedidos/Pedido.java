package pedidos;

public record Pedido(int id, String cliente, String producto, int cantidad, String estado) {
}
