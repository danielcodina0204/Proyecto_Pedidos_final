# Sistema Distribuido de Gestión de Pedidos Tolerante a Fallos

Aplicación web para crear, listar, actualizar y eliminar pedidos, con control de estado (pendiente, en proceso, enviado, entregado). El sistema funciona sobre dos nodos backend balanceados por NGINX y una base de datos PostgreSQL con réplica, por lo que sigue operando ante la caída de un nodo o de la base de datos primaria.

**Proyecto Final de Sistemas Distribuidos**

- Integrantes: Daniel De Jesus Codina Ortiz, Moises David Zambrano y Juan Pablo Gutierez
- Docente: Helbert Fernando Valencia Barrero

## Estructura

```
frontend/        Interfaz en Angular y configuración de NGINX (balanceador)
backend/         API REST en Java con Spring Boot y pruebas automatizadas
base_de_datos/   Esquema SQL y scripts de replicación de PostgreSQL
documentacion/   Manual técnico, manual de usuario e informe de pruebas
docker-compose.yml
.env.example
```

## Requisitos

- Docker Desktop con Docker Compose v2.
- JDK 25 o superior, solo para ejecutar las pruebas. Maven se descarga automáticamente con el wrapper `mvnw`.

## Despliegue

1. Crear el archivo de configuración y cambiar las contraseñas de ejemplo:

   ```bash
   cp .env.example .env
   ```

   En PowerShell: `Copy-Item .env.example .env`

2. Construir y levantar los cinco contenedores (NGINX, dos nodos backend, base primaria y réplica):

   ```bash
   docker compose up -d --build --wait
   ```

3. Abrir <http://localhost:8080>.

Para detener el sistema: `docker compose down`. Para borrar además los datos: `docker compose down -v`.

## Pruebas

```bash
cd backend
./mvnw test      # Pruebas unitarias; no requieren el sistema levantado
./mvnw verify    # Unitarias, de integración y de fallo; requieren el sistema levantado
```

En Windows se usa `mvnw.cmd` en lugar de `./mvnw`.

- `DatosPedidoTest`: pruebas unitarias de validación.
- `IntegracionIT`: pruebas de integración a través de NGINX.
- `FallosIT`: pruebas de fallo; detienen y vuelven a levantar contenedores.

## Simular fallos manualmente

```bash
docker compose stop backend1     # El tráfico pasa a nodo-2
docker compose start backend1
docker compose stop db-primary   # Lecturas desde la réplica, escrituras en modo solo lectura
docker compose start db-primary
```

## Documentación

- [Manual técnico](documentacion/manual_tecnico.md)
- [Manual de usuario](documentacion/manual_usuario.md)
- [Informe de pruebas](documentacion/informe_pruebas.md)
