import LDBCTest.MyGraph
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
  def queryTest(): Unit = {
    val q =
      """
        |MATCH (n:person {id: $personId })-[:knows]-(friend3:person)
        |RETURN
        |  friend3.id AS personId,
        |  friend3.firstName AS firstName,
        |  friend3.lastName AS lastName
        |""".stripMargin
    val p = Map("personId" -> "443")
    val startTime1 = System.currentTimeMillis()
    MyGraph.run(q, p).show()
    System.out.println("程序运行时间： " + (System.currentTimeMillis() - startTime1) + "ms")
    //    val startTime = System.currentTimeMillis()
    //    MyGraph.run(q, p).show()
    //    System.out.println("程序运行时间： " + (System.currentTimeMillis() - startTime) + "ms")
  }

  @Test
  def loadSchema(): Unit = {
    var schema = SchemaManager.readJson("SF11.json")
    println(111)
    println(schema.tables)
    schema.gRelationship.size == 1

  }


  @Test
  def Q7(): Unit = {
    val q =
      """
        |MATCH (n:person {id: $personId })-[:knows*3]-(friend3:person)
        |RETURN
        |    friend3.id AS personId,
        |    friend3.firstName AS firstName,
        |    friend3.lastName AS lastName
        |""".stripMargin
    val p = Map("personId" -> "443")
    //预热
    //    MyGraph.run(q, p).show()
//    val startTime1 = System.currentTimeMillis()
//    MyGraph.run(q, p).show()
//    System.out.println("程序运行时间： " + (System.currentTimeMillis() - startTime1) + "ms")
    val startTime = System.currentTimeMillis()
    MyGraph.run(q, p).show()
    System.out.println("程序运行时间： " + (System.currentTimeMillis() - startTime) + "ms")
  }
}
