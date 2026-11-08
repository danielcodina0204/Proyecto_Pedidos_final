package pedidos;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Aplicacion {

    public static void main(String[] args) {
        SpringApplication.run(Aplicacion.class, args);
    }

    /** Agrega a cada respuesta la cabecera X-Node con el nodo que la atendió. */
    @Bean
    Filter identificarNodo(@Value("${NODO_ID}") String nodo) {
        return (peticion, respuesta, cadena) -> {
            ((HttpServletResponse) respuesta).setHeader("X-Node", nodo);
            cadena.doFilter(peticion, respuesta);
        };
    }
}
