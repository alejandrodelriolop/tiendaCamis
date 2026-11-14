package es.camisetas;

import es.camisetas.config.GestorDB;

class MenuMySQLTest extends MenuTest {

    @Override
    protected GestorDB gestorBD() {
        return GestorDB.MYSQL;
    }
}