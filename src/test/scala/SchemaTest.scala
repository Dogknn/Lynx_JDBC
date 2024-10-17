import org.junit.jupiter.api.Test
import schema.{Schema, SchemaManager}

import java.sql.DriverManager

class SchemaTest {
  @Test
  def autoGen(): Unit = {
    SchemaManager.saveJson("SF11.json", SchemaManager.autoGeneration(
      DriverManager.getConnection(
        "jdbc:mysql://49.232.149.246:3306/LDBCSF10?serverTimezone=UTC&useUnicode=true&characterEncoding=utf8&useSSL=false",
        "root", "1020@Wwt")))
  }

  @Test
  def loadSchema(): Unit = {
    var schema = SchemaManager.readJson("SF11.json")
    println(111)
    println(schema.tables)
    schema.gRelationship.size ==1

  }
}
