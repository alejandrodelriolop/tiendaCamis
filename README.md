# Tienda de camisetas de fútbol · Práctica UD2 (JDBC)

Aplicación Java (sin interfaz) que simula una tienda online de **camisetas de fútbol de distintos equipos, eras y jugadores**
(Brasil 1970 de Pelé, Argentina 1986 de Maradona, Barça 2006 de Ronaldinho…). Persiste en **MySQL o H2** con **JDBC directo**
y obtiene conexiones con **DriverManager o HikariCP**; cambiar de gestor o de forma de conectar solo exige editar `application.properties`.

- **Repositorio:** https://github.com/alejandrodelriolop/tiendaCamis

## Qué la hace una tienda de camisetas (y no de otra cosa)

| Característica de la temática | Dónde influye |
|---|---|
| **Equipo + Era + Jugador** de cada camiseta (el jugador es opcional, con su dorsal) | Relaciones 1:N, filtros del catálogo y consulta *«carrera de un jugador»* |
| **Edición** `REPLICA / JUGADOR / RETRO / COLECCIONISTA` | Regla de negocio: máximo **2 unidades por pedido** de una edición coleccionista |
| Camisetas **bajo pedido** (`control_stock = false`) | Se venden sin descontar stock ni generar movimientos |
| Camisetas **descatalogadas** (`activa = false`) | No se pueden comprar ni salen como disponibles |
| Ventas **por era** | Consulta propia de la temática |

## Modelo de datos (13 tablas)

Diagrama completo: [`docs/modelo-er.png`](docs/modelo-er.png) (fuente editable: [`docs/docs/modelo-er.mmd`](docs/docs/modelo-er.mmd)).

![Modelo entidad-relación](docs/docs/modelo-er.png)

- **1:N:** equipo→camiseta, era→camiseta, jugador→camiseta, cliente→dirección, cliente→pedido, pedido→línea, camiseta→movimiento.
- **N:M:** `camiseta` ↔ `categoria` mediante la tabla intermedia `camiseta_categoria`.
- **1:1:** `camiseta` ↔ `inventario` (la PK es a la vez FK) y `pedido` ↔ `pago` (FK con `UNIQUE`).
- `linea_pedido.precio_unitario` guarda el **precio aplicado al comprar**: cambiar el catálogo no altera pedidos anteriores.
- `movimiento_stock` explica cada entrada (`ENTRADA_REPOSICION`, `ENTRADA_CANCELACION`) y salida (`SALIDA_PEDIDO`) de stock.
- Importes `DECIMAL` ↔ `BigDecimal`; fechas `DATE/TIMESTAMP` (`DATETIME` en MySQL) ↔ `LocalDate/LocalDateTime`.

## Estructura

```
src/main/java/es/camisetas/
  main/                arranque: configuración → inicialización → simulación
  app/                 MenuPrincipal, MenuTienda (modo normal), Simulacion, Consola y Presentacion
  config/              Configuracion (application.properties) y enums de modo
  connections/                  ConnectionProvider (+DriverManager / HikariCP), Transacciones, SqlScriptRunner
  init/                DatabaseInitializer + DatosIniciales (carga por lotes)
  model/               Entidades 
  dto/                 DTO de consultas
  dao/                 JDBC puro: reciben la Connection, no imprimen, no saben quién la creó
  service/             lógica comercial; deciden commit/rollback
  exception/           técnicas (Persistencia, Conexion, Configuracion) vs. de negocio (NegocioException)
  utilidades/          contiene DataBaseUtils
src/main/resources/    log4j2.xml, sql/h2/, sql/mysql/ (esquema y procedimiento almacenado)
src/test/              JUnit 5 sobre H2 real
docs/                  diagrama entidad-relación
logs/evidencias/       evidencias completas de ejecuciones (versionadas)
```

## Requisitos

- **Java 17 o superior** y **Maven 3.9+** (las dependencias —H2, MySQL Connector/J, HikariCP, Log4j 2, JUnit 5— las descarga Maven).
- **H2:** no necesita instalación (base en memoria).
- **MySQL 8.0.16+** (por las restricciones `CHECK`). Una base `tiendaCamis` se crea sola si el usuario tiene permiso (`createDatabaseIfNotExist=true`):

  ```sql
  CREATE USER 'tienda'@'localhost' IDENTIFIED BY 'pon-aqui-tu-clave';
  GRANT ALL PRIVILEGES ON tiendaCamis.* TO 'tienda'@'localhost';
  GRANT CREATE ON *.* TO 'tienda'@'localhost';   -- solo si quieres que cree la base automáticamente
  ```

## Configuración

```bash
cp application.properties.example application.properties   # este fichero NO se versiona
```

| Propiedad | Valores | Efecto |
|---|---|---|
| `database.engine` | `MYSQL` / `H2` | Base de datos usada |
| `connection.mode` | `DRIVER_MANAGER` / `POOL` | Conexión directa o pool HikariCP (se cierra al terminar) |
| `app.interactive` | `true` (por defecto) / `false` | `true`: al arrancar se muestra un **menú**. `false`: sin menú, ejecuta directamente la simulación de `simulation.mode` |
| `simulation.mode` | `NORMAL` / `FALLO_TRANSACCION` | Solo con `app.interactive=false`: recorrido completo o demostración de rollback |

Los datos de MySQL (`mysql.url`, `mysql.user`, `mysql.password`) y del pool (`pool.*`) están en el mismo fichero.
La contraseña real solo va en tu `application.properties` local. Se puede usar otro fichero con `-Dapp.config=ruta/otro.properties`.

## Ejecución

```bash
mvn clean package                                   # compila, pasa las pruebas y copia las dependencias a target/lib
java -jar target/tienda-camisetas-1.0.0.jar         # usa ./application.properties
# o, sin empaquetar:
mvn exec:java
# otro fichero de configuración (p. ej. uno por cada combinación):
java -Dapp.config=config/mysql-pool.properties -jar target/tienda-camisetas-1.0.0.jar
```

Las **4 combinaciones** (`MYSQL`/`H2` × `DRIVER_MANAGER`/`POOL`) ejecutan exactamente los mismos DAO y servicios.
Cada arranque **reconstruye** (`DROP`/`CREATE`) solo las tablas de la práctica y recarga los mismos datos, sin intervención manual.

### Menú de arranque

Con `app.interactive=true` (valor por defecto) la aplicación prepara la base de datos y muestra:

```
1. Entrar en la tienda (modo normal)
2. Simulación automática (recorrido completo)
3. Simulación de fallo de transacción (demostración de rollback)
4. Reiniciar la base de datos con los datos de partida
0. Salir
```

- **Tienda (modo normal):** catálogo con filtros y páginas, detalle de camiseta, alta y consulta de clientes, hacer / ver / pagar / cancelar
  pedidos, reponer stock y consultas de gestión. Es la misma lógica de los servicios que usa la simulación; los cambios quedan en la base de datos.
- **Simulaciones (2 y 3):** parten siempre de un estado conocido, así que **reconstruyen la base de datos** (antes piden confirmación) y descartan
  lo hecho en la tienda.
- Para generar evidencias o ejecutar sin teclear, pon `app.interactive=false` y elige `simulation.mode`.

### Qué hace cada modo de simulación

- **`NORMAL`:** catálogo paginado y filtrado → clientes → reposición y movimientos → pedidos con varios productos + pago simulado
  → rechazos por reglas de negocio → cancelación (reembolso y devolución de stock) y segunda cancelación rechazada
  → consultas de gestión (DTO) → procedimiento almacenado → violaciones de integridad reales → comprobación final de coherencia.
- **`FALLO_TRANSACCION`:** registra un pedido, lo modifica en varias tablas (pedido, líneas, stock, movimientos), lanza un fallo
  controlado **antes del commit**, hace rollback y **consulta la base de datos con SQL** para comprobar que no queda nada
  (mismos recuentos, ningún pedido sin líneas, mismo stock). Si la verificación fallase, el programa termina con error.

## Pruebas

```bash
mvn test
```

JUnit 5 contra H2 real (una base nueva por prueba, con el esquema y los datos reales de la aplicación): inserción y consulta,
UPDATE/DELETE, claves generadas, consultas con JOIN/agregaciones a DTO, paginación, commit, rollback (en ambos modos de conexión),
cancelación sin doble devolución, ciclo de vida del pago, violaciones de integridad (SQLState `23505`, `23506`, `23513`) y configuración.

## Logs y evidencias

- `logs/runtime/aplicacion.log`: log activo con rotación por tamaño (20 KB), históricos `aplicacion-AAAA-MM-DD-N.log.gz` y máximo 5 (no se versiona).
- `logs/evidencias/ejecucion_dd-MM-yyyy_HH-mm-ss.log`: un fichero completo **por ejecución** (sí se versiona). Se generan solos al ejecutar:

  ```bash
  # (con app.interactive=false; también puedes lanzarlas desde el menú, opciones 2 y 3)
  # 1) Normal sobre MySQL  → database.engine=MYSQL, simulation.mode=NORMAL
  # 2) Normal sobre H2     → database.engine=H2,    simulation.mode=NORMAL
  # 3) Rollback            → simulation.mode=FALLO_TRANSACCION
  ```

  Conserva en Git las tres evidencias de la versión final (borra a mano las ejecuciones de desarrollo que no quieras publicar; no las edites).

## Diferencias conocidas entre MySQL y H2

| | MySQL | H2 |
|---|---|---|
| Procedimiento almacenado `sp_resumen_equipo` (IN/OUT, `CallableStatement`) | Se crea y se invoca desde la simulación | **No existe**: la aplicación lo detecta, lo registra en el log y no falla |
| DDL | `AUTO_INCREMENT`, `DATETIME` | `GENERATED BY DEFAULT AS IDENTITY`, `TIMESTAMP` (`sql/h2/` y `sql/mysql/`) |
| Código de error de `CHECK` | `3819` con SQLState `HY000` | `23513` con SQLState `23513` |
| `UNIQUE` / FK | SQLState `23000` (códigos `1062` / `1452`) | `23505` / `23506` |

Que el procedimiento no esté en H2 es una limitación del gestor, no un fallo de la aplicación.

## Decisiones técnicas

- **Una transacción = una `Connection`.** Los servicios abren la transacción (`Transacciones.ejecutar`) y pasan esa misma conexión a todos los DAO;
  los DAO nunca abren, confirman ni cierran conexiones.
- **Stock sin carreras evidentes:** el descuento es `UPDATE … SET stock = stock - ? WHERE … AND stock >= ?` y la cancelación
  `UPDATE pedido … WHERE estado <> 'CANCELADO'`; si afecta a 0 filas se rechaza (no puede devolverse el stock dos veces).
- **Errores separados:** `NegocioException` (previstos), `PersistenciaException` (guarda SQLState y código del driver),
  `ConexionException` y `ConfiguracionException` (infraestructura).
- **Sin ORM:** solo `Connection`, `PreparedStatement`, `ResultSet`, `CallableStatement` y lotes (`addBatch`/`executeBatch`) con `getGeneratedKeys()`.
- Las consultas usan parámetros (`?`); el único SQL con partes dinámicas (filtros del catálogo) añade solo fragmentos fijos y enlaza los valores.

## Uso de IA

Ver [`IA.md`](IA.md).
