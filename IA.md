# Uso de inteligencia artificial

## Herramientas utilizadas

### Herramienta 1
- Nombre: ChatGPT (OpenAI).
- Forma de uso: chat web. Consultas sobre diseño, JDBC, configuración de H2 y MySQL,
  transacciones, excepciones, Log4j 2 y JUnit 5; revisión de fragmentos de código,
  localización de errores y ayuda con la documentación.

### Herramienta 2
- Nombre: Claude (Anthropic)
- Forma de uso: chat / agente de código. Revisión de la práctica.

Las propuestas de la IA se han revisado, adaptado al proyecto y comprobado
ejecutando el programa y las pruebas. La responsabilidad sobre el código
entregado es del autor.

«¿Cómo puedo organizar una aplicación Java con JDBC separando modelos, DAO, servicios y conexiones?»

«¿Cómo diseño las relaciones entre camisetas, equipos, jugadores, categorías, clientes y pedidos? Necesito relaciones 1:N, N:M y 1:1.»

«¿Cómo puedo configurar mi aplicación para cambiar entre MySQL y H2 mediante un archivo application.properties?»

«¿Cómo puedo utilizar DriverManager o HikariCP sin cambiar el código de los DAO?»

«¿Cómo recupero el ID generado al insertar un cliente para utilizarlo al guardar sus direcciones?»

«¿Cómo hago que la creación de un pedido, sus líneas y el descuento de stock se ejecuten en una única transacción JDBC?»

«¿Cómo puedo provocar un fallo antes del commit y comprobar que el rollback deshace todos los cambios del pedido?»

«¿Cómo evito que el stock quede negativo cuando se intenta comprar una cantidad superior a la disponible?»

«¿Cómo implemento la cancelación de un pedido para devolver el stock y evitar que una segunda cancelación lo devuelva otra vez?»

«¿Por qué debería usar BigDecimal para los precios y guardar el precio de compra en cada línea del pedido?»

«¿Cómo construyo una consulta de catálogo con filtros opcionales y paginación utilizando PreparedStatement?»

«¿Cómo puedo consultar las camisetas más vendidas y las ventas por era usando JOIN, GROUP BY y SUM, y devolver los resultados en DTO?»

«¿Cómo puedo probar un menú de consola con JUnit 5 simulando la entrada del usuario y capturando la salida?»

«Mi test usa assertThrows y termina con código 0, pero aparece una traza de error de persistencia. ¿Por qué ocurre y cómo puedo silenciar los logs de Transacciones solo durante las pruebas?»

«En ConfiguracionTest falla la comprobación de la URL de H2. ¿Cómo puedo definir h2.url explícitamente para comprobar que se seleccionan los parámetros del gestor correcto?»