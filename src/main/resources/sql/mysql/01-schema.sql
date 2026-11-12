-- Esquema de la tienda de camisetas para MySQL.
-- Se reconstruye en cada ejecución: solo afecta a las tablas de esta práctica.

DROP TABLE IF EXISTS pago;
DROP TABLE IF EXISTS movimiento_stock;
DROP TABLE IF EXISTS linea_pedido;
DROP TABLE IF EXISTS pedido;
DROP TABLE IF EXISTS direccion;
DROP TABLE IF EXISTS cliente;
DROP TABLE IF EXISTS inventario;
DROP TABLE IF EXISTS camiseta_categoria;
DROP TABLE IF EXISTS camiseta;
DROP TABLE IF EXISTS categoria;
DROP TABLE IF EXISTS jugador;
DROP TABLE IF EXISTS era;
DROP TABLE IF EXISTS equipo;

CREATE TABLE equipo (
                        id      BIGINT AUTO_INCREMENT PRIMARY KEY,
                        nombre  VARCHAR(80) NOT NULL,
                        pais    VARCHAR(60) NOT NULL,
                        liga    VARCHAR(60) NOT NULL,
                        CONSTRAINT uk_equipo_nombre UNIQUE (nombre)
);

CREATE TABLE era (
                     id           BIGINT AUTO_INCREMENT PRIMARY KEY,
                     nombre       VARCHAR(40) NOT NULL,
                     anio_inicio  INT NOT NULL,
                     anio_fin     INT NULL,
                     CONSTRAINT uk_era_nombre UNIQUE (nombre),
                     CONSTRAINT ck_era_anios CHECK (anio_fin IS NULL OR anio_fin >= anio_inicio)
);

CREATE TABLE jugador (
                         id        BIGINT AUTO_INCREMENT PRIMARY KEY,
                         nombre    VARCHAR(80) NOT NULL,
                         pais      VARCHAR(60) NOT NULL,
                         posicion  VARCHAR(30) NOT NULL,
                         CONSTRAINT uk_jugador_nombre UNIQUE (nombre)
);

CREATE TABLE categoria (
                           id           BIGINT AUTO_INCREMENT PRIMARY KEY,
                           nombre       VARCHAR(50) NOT NULL,
                           descripcion  VARCHAR(200) NULL,
                           CONSTRAINT uk_categoria_nombre UNIQUE (nombre)
);

-- Producto del catálogo: una camiseta de un equipo, de una era y (opcionalmente) de un jugador.

CREATE TABLE camiseta (
                          id             BIGINT AUTO_INCREMENT PRIMARY KEY,
                          sku            VARCHAR(30) NOT NULL, -- Stock Keeping Unit (unidad de mantenimiento de existencias).
                          nombre         VARCHAR(120) NOT NULL,
                          equipo_id      BIGINT NOT NULL,
                          era_id         BIGINT NOT NULL,
                          jugador_id     BIGINT NULL,
                          dorsal         INT NULL,
                          temporada      VARCHAR(10) NOT NULL,
                          equipacion     VARCHAR(15) NOT NULL,
                          edicion        VARCHAR(15) NOT NULL,
                          precio         DECIMAL(8,2) NOT NULL,
                          activa         BOOLEAN NOT NULL DEFAULT TRUE,
                          control_stock  BOOLEAN NOT NULL DEFAULT TRUE,
                          CONSTRAINT uk_camiseta_sku UNIQUE (sku),
                          CONSTRAINT fk_camiseta_equipo  FOREIGN KEY (equipo_id)  REFERENCES equipo (id),
                          CONSTRAINT fk_camiseta_era     FOREIGN KEY (era_id)     REFERENCES era (id),
                          CONSTRAINT fk_camiseta_jugador FOREIGN KEY (jugador_id) REFERENCES jugador (id),
                          CONSTRAINT ck_camiseta_precio     CHECK (precio > 0),
                          CONSTRAINT ck_camiseta_dorsal     CHECK (dorsal IS NULL OR (dorsal BETWEEN 1 AND 99)),
                          CONSTRAINT ck_camiseta_equipacion CHECK (equipacion IN ('LOCAL', 'VISITANTE', 'TERCERA', 'PORTERO')),
                          CONSTRAINT ck_camiseta_edicion    CHECK (edicion IN ('REPLICA', 'JUGADOR', 'RETRO', 'COLECCIONISTA'))
);

-- Relación N:M camiseta <-> categoría
CREATE TABLE camiseta_categoria (
                                    camiseta_id   BIGINT NOT NULL,
                                    categoria_id  BIGINT NOT NULL,
                                    PRIMARY KEY (camiseta_id, categoria_id),
                                    CONSTRAINT fk_cc_camiseta  FOREIGN KEY (camiseta_id)  REFERENCES camiseta (id) ON DELETE CASCADE,
                                    CONSTRAINT fk_cc_categoria FOREIGN KEY (categoria_id) REFERENCES categoria (id) ON DELETE CASCADE
);

-- Relación 1:1 camiseta <-> inventario (la clave primaria es a la vez clave foránea)
CREATE TABLE inventario (
                            camiseta_id       BIGINT PRIMARY KEY,
                            stock             INT NOT NULL,
                            umbral_reposicion INT NOT NULL DEFAULT 0,
                            CONSTRAINT fk_inventario_camiseta FOREIGN KEY (camiseta_id) REFERENCES camiseta (id),
                            CONSTRAINT ck_inventario_stock  CHECK (stock >= 0),
                            CONSTRAINT ck_inventario_umbral CHECK (umbral_reposicion >= 0)
);

CREATE TABLE cliente (
                         id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                         nombre      VARCHAR(100) NOT NULL,
                         email       VARCHAR(120) NOT NULL,
                         fecha_alta  DATE NOT NULL,
                         CONSTRAINT uk_cliente_email UNIQUE (email)
);

-- Relación 1:N cliente -> direcciones
CREATE TABLE direccion (
                           id             BIGINT AUTO_INCREMENT PRIMARY KEY,
                           cliente_id     BIGINT NOT NULL,
                           calle          VARCHAR(150) NOT NULL,
                           ciudad         VARCHAR(80) NOT NULL,
                           codigo_postal  VARCHAR(10) NOT NULL,
                           pais           VARCHAR(60) NOT NULL,
                           principal      BOOLEAN NOT NULL DEFAULT FALSE,
                           CONSTRAINT fk_direccion_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id)
);

-- Relación 1:N cliente -> pedidos
CREATE TABLE pedido (
                        id            BIGINT AUTO_INCREMENT PRIMARY KEY,
                        cliente_id    BIGINT NOT NULL,
                        direccion_id  BIGINT NOT NULL,
                        fecha         DATETIME NOT NULL,
                        estado        VARCHAR(15) NOT NULL,
                        total         DECIMAL(10,2) NOT NULL DEFAULT 0,
                        CONSTRAINT fk_pedido_cliente   FOREIGN KEY (cliente_id)   REFERENCES cliente (id),
                        CONSTRAINT fk_pedido_direccion FOREIGN KEY (direccion_id) REFERENCES direccion (id),
                        CONSTRAINT ck_pedido_estado CHECK (estado IN ('PENDIENTE', 'PAGADO', 'CANCELADO')),
                        CONSTRAINT ck_pedido_total  CHECK (total >= 0)
);

-- Detalle de lo comprado: conserva el precio aplicado en el momento de la compra
CREATE TABLE linea_pedido (
                              id               BIGINT AUTO_INCREMENT PRIMARY KEY,
                              pedido_id        BIGINT NOT NULL,
                              camiseta_id      BIGINT NOT NULL,
                              cantidad         INT NOT NULL,
                              precio_unitario  DECIMAL(8,2) NOT NULL,
                              importe          DECIMAL(10,2) NOT NULL,
                              CONSTRAINT uk_linea_pedido_camiseta UNIQUE (pedido_id, camiseta_id),
                              CONSTRAINT fk_linea_pedido   FOREIGN KEY (pedido_id)   REFERENCES pedido (id),
                              CONSTRAINT fk_linea_camiseta FOREIGN KEY (camiseta_id) REFERENCES camiseta (id),
                              CONSTRAINT ck_linea_cantidad CHECK (cantidad > 0),
                              CONSTRAINT ck_linea_precio   CHECK (precio_unitario > 0)
);

-- Historial de existencias: explica cada entrada y salida de stock
CREATE TABLE movimiento_stock (
                                  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
                                  camiseta_id  BIGINT NOT NULL,
                                  pedido_id    BIGINT NULL,
                                  tipo         VARCHAR(25) NOT NULL,
                                  cantidad     INT NOT NULL,
                                  fecha        DATETIME NOT NULL,
                                  motivo       VARCHAR(150) NULL,
                                  CONSTRAINT fk_mov_camiseta FOREIGN KEY (camiseta_id) REFERENCES camiseta (id),
                                  CONSTRAINT fk_mov_pedido   FOREIGN KEY (pedido_id)   REFERENCES pedido (id),
                                  CONSTRAINT ck_mov_cantidad CHECK (cantidad > 0),
                                  CONSTRAINT ck_mov_tipo CHECK (tipo IN ('ENTRADA_REPOSICION', 'SALIDA_PEDIDO', 'ENTRADA_CANCELACION'))
);

-- Relación 1:1 pedido <-> pago (UNIQUE sobre pedido_id)
CREATE TABLE pago (
                      id         BIGINT AUTO_INCREMENT PRIMARY KEY,
                      pedido_id  BIGINT NOT NULL,
                      metodo     VARCHAR(20) NOT NULL,
                      estado     VARCHAR(15) NOT NULL,
                      importe    DECIMAL(10,2) NOT NULL,
                      fecha      DATETIME NOT NULL,
                      CONSTRAINT uk_pago_pedido UNIQUE (pedido_id),
                      CONSTRAINT fk_pago_pedido FOREIGN KEY (pedido_id) REFERENCES pedido (id),
                      CONSTRAINT ck_pago_metodo CHECK (metodo IN ('TARJETA', 'PAYPAL', 'TRANSFERENCIA', 'BIZUM')),
                      CONSTRAINT ck_pago_estado CHECK (estado IN ('COMPLETADO', 'REEMBOLSADO')),
                      CONSTRAINT ck_pago_importe CHECK (importe >= 0)
);
