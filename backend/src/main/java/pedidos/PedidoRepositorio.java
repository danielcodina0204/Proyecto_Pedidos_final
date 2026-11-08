package pedidos;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Repository;

/** Acceso a PostgreSQL: escrituras en la primaria y lecturas con respaldo en la réplica. */
@Repository
public class PedidoRepositorio {

    private static final String CAMPOS = "id, cliente, producto, cantidad, estado";

    private final JdbcClient primaria;
    private final JdbcClient replica;

    PedidoRepositorio(@Value("${DB_PRIMARIA_URL}") String urlPrimaria, @Value("${DB_REPLICA_URL}") String urlReplica,
            @Value("${DB_USUARIO}") String usuario, @Value("${DB_CLAVE}") String clave) {
        primaria = conectar(urlPrimaria, usuario, clave);
        replica = conectar(urlReplica, usuario, clave);
    }

    // shortcut: una conexión por consulta, sin pool; se recupera sola tras una caída. Usar HikariCP si crece la carga.
    private static JdbcClient conectar(String url, String usuario, String clave) {
        return JdbcClient.create(new DriverManagerDataSource(url, usuario, clave));
    }

    public List<Pedido> listar() {
        var sql = "SELECT " + CAMPOS + " FROM pedidos ORDER BY id DESC";
        try {
            return primaria.sql(sql).query(Pedido.class).list();
        } catch (DataAccessResourceFailureException primariaCaida) {
            return replica.sql(sql).query(Pedido.class).list();
        }
    }

    public Pedido crear(DatosPedido datos) {
        return escribir(() -> primaria.sql("""
                INSERT INTO pedidos (cliente, producto, cantidad, estado)
                VALUES (?, ?, ?, COALESCE(CAST(? AS VARCHAR), 'pendiente')) RETURNING\s""" + CAMPOS)
                .params(datos.cliente(), datos.producto(), datos.cantidad(), datos.estado())
                .query(Pedido.class).single());
    }

    public Optional<Pedido> actualizar(int id, DatosPedido datos) {
        return escribir(() -> primaria.sql("""
                UPDATE pedidos SET cliente = ?, producto = ?, cantidad = ?, estado = COALESCE(CAST(? AS VARCHAR), estado)
                WHERE id = ? RETURNING\s""" + CAMPOS)
                .params(datos.cliente(), datos.producto(), datos.cantidad(), datos.estado(), id)
                .query(Pedido.class).optional());
    }

    public Optional<Pedido> cambiarEstado(int id, String estado) {
        return escribir(() -> primaria.sql("UPDATE pedidos SET estado = ? WHERE id = ? RETURNING " + CAMPOS)
                .params(estado, id)
                .query(Pedido.class).optional());
    }

    public boolean eliminar(int id) {
        return escribir(() -> primaria.sql("DELETE FROM pedidos WHERE id = ?").param(id).update() > 0);
    }

    public boolean primariaDisponible() {
        return responde(primaria);
    }

    public boolean replicaDisponible() {
        return responde(replica);
    }

    private static <T> T escribir(Supplier<T> operacion) {
        try {
            return operacion.get();
        } catch (DataAccessResourceFailureException error) {
            throw new PrimariaNoDisponible(error);
        }
    }

    private static boolean responde(JdbcClient baseDeDatos) {
        try {
            baseDeDatos.sql("SELECT 1").query().singleValue();
            return true;
        } catch (DataAccessResourceFailureException error) {
            return false;
        }
    }

    /** La base primaria no responde: el sistema queda en modo solo lectura. */
    public static class PrimariaNoDisponible extends RuntimeException {
        private static final long serialVersionUID = 1L;

        PrimariaNoDisponible(Throwable causa) {
            super(causa);
        }
    }
}
