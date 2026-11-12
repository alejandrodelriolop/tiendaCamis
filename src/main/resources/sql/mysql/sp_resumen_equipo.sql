CREATE PROCEDURE sp_resumen_equipo(
    IN  p_equipo_id BIGINT,
    OUT p_unidades  INT,
    OUT p_ingresos  DECIMAL(12,2),
    OUT p_pedidos   INT
)
BEGIN
    -- Resumen comercial de un equipo: camisetas vendidas, ingresos y pedidos distintos.
    -- Los pedidos cancelados no cuentan como venta.
    SELECT COALESCE(SUM(lp.cantidad), 0),
           COALESCE(SUM(lp.importe), 0),
           COUNT(DISTINCT p.id)
    INTO p_unidades, p_ingresos, p_pedidos
    FROM linea_pedido lp
             JOIN pedido p   ON p.id = lp.pedido_id
             JOIN camiseta c ON c.id = lp.camiseta_id
    WHERE c.equipo_id = p_equipo_id
      AND p.estado <> 'CANCELADO';
END