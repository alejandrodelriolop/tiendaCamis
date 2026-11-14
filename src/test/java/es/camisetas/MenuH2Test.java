package es.camisetas;

import es.camisetas.config.GestorDB;

class MenuH2Test extends MenuTest {

    @Override
    protected GestorDB gestorBD() {
        return GestorDB.H2;
    }
}