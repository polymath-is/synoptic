package synopticgwt.server;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Test an instance of a Derby instance.
 */
public class DerbyDBTests {
    /** Database name and path */
    private static String path = "/Users/Kevin/Desktop/DerbyUnitTest";
        
    /**
     * Create a new db and table. Writes to the table. Check that read data is
     * the same as written data.
     * Note: To use test, make sure database name/path doesn't already exist.
     */
    @Test
    public void testNewDatabase() {
        DerbyDB db = DerbyDB.getInstance(path, true);
        String createNewTable = "CREATE TABLE Test (id INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1), text VARCHAR(15))";
        db.createQuery(createNewTable);
        assertNotNull(db);
        
        String writeToTable = "INSERT INTO test(text) values('test insert')";
        int id = db.insertAndGetAutoValue(writeToTable);
        assertEquals(1, id);  
              
        String readTable = "SELECT text from Test where id = " + id;
        String result = db.getString(readTable, "text");
        assertEquals("test insert", result);
        
        db.shutdown();
    }
}