import LDBCTest.MyGraph
import LynxJDBCElement.LynxJDBCConnector
import org.grapheco.lynx.types.time.LynxDate
import org.junit.{Assert, Test}
import schema.SchemaManager

import java.io.File
import java.time.LocalDate
import scala.io.Source


class LDBCTest {


  @Test
  def t1(): Unit = {
    val query =
      """
        |MATCH (n:person {id: $personId })-[r:KNOWS]-(friend)
        |RETURN
        |    friend.id AS personId,
        |    friend.firstName AS firstName,
        |    friend.lastName AS lastName,
        |    r.creationDate AS friendshipCreationDate
        |ORDER BY
        |    friendshipCreationDate DESC,
        |    toInteger(personId) ASC
        |""".stripMargin
    val param = Map("personId" -> "1929")
    MyGraph.run(query, param).show()

    val startTime = System.currentTimeMillis()
    MyGraph.run(query, param).show()
    System.out.println("程序运行时间： " + (System.currentTimeMillis() - startTime) + "ms")
  }

  @Test
  def t2(): Unit = {
    val query =
      """
        |MATCH (:Person {id: $personId })-[:knows*1..2]-(friend:Person)
        |    RETURN
        |        friend.id AS personId,
        |        friend.firstName AS personFirstName
        |    LIMIT 20
        |""".stripMargin
    val param = Map("personId" -> "1929")
    MyGraph.run(query, param).show()

    val startTime = System.currentTimeMillis()
    MyGraph.run(query, param).show()
    System.out.println("程序运行时间： " + (System.currentTimeMillis() - startTime) + "ms")
  }
}

object LDBCTest {
  val MyGraph: LynxJDBCConnector = LynxJDBCConnector.connect(
    "jdbc:mysql://49.232.149.246:3306/LDBCSF10?serverTimezone=UTC&useUnicode=true&characterEncoding=utf8&useSSL=false",
    "root", "1020@Wwt"
    , SchemaManager.readJson("SF11.json"))
}
