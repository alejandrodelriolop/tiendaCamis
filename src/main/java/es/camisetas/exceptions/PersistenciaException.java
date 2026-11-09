package es.camisetas.exceptions;

import es.camisetas.utilidades.DatabaseUtils;
import java.sql.SQLException;

public class PersistenciaException extends RuntimeException {

  private final String sqlState;
  private final int errorCode;

  public PersistenciaException(String mensaje, SQLException causa) {
    super(mensaje + ": " + causa.getMessage(), causa);
    this.sqlState = causa.getSQLState();
    this.errorCode = causa.getErrorCode();
  }

  public String getSqlState() {
    return sqlState;
  }

  public int getErrorCode() {
    return errorCode;
  }

  /** Mensaje original del driver, sin el contexto añadido por la aplicación. */
  public String getMensajeDriver() {
    return getCause().getMessage();
  }

  /** Imprime los detalles del error SQL original utilizando la utilidad. */
  public void imprimirDetallesSql() {
    if (getCause() instanceof SQLException) {
      DatabaseUtils.imprimirDetallesSQLException((SQLException) getCause());
    }
  }

  /**
   * Violación de restricción de integridad: SQLState de la clase 23 (estándar SQL, lo que usan H2 y MySQL
   * para UNIQUE y claves foráneas). MySQL informa las restricciones CHECK con el código propio 3819 y SQLState HY000.
   */
  public boolean esViolacionIntegridad() {
    return (sqlState != null && sqlState.startsWith("23")) || errorCode == CODIGO_MYSQL_CHECK;
  }

  private static final int CODIGO_MYSQL_CHECK = 3819;
}