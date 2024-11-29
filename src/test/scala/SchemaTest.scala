import LDBCTest.MyGraph
import db.LynxJDBCConnector
import org.junit.jupiter.api.Test
import schema.{Schema, SchemaManager}

import java.sql.{Connection, DriverManager, ResultSet, Statement}

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

  def loaddata(connection: Connection): Unit = {
    val query =
      s"""
         |SELECT p.Person_id, p.OtherPerson_id
         |FROM person_knows_person p
         |JOIN (
         |  SELECT Person_id
         |  FROM person_knows_person
         |  GROUP BY Person_id
         |  ORDER BY COUNT(*) DESC
         |  LIMIT 500
         |) AS top_persons ON p.Person_id = top_persons.Person_id;
         |
         |""".stripMargin


    val statement = connection.createStatement()
    val result = statement.executeQuery(query)
    Iterator.continually(result).takeWhile(_.next())
  }

  @Test
  def Q7(): Unit = {
    val q =
      """
        |MATCH (n:person {id: $personId })-[:knows*4]-(friend3:person)
        |RETURN
        |    friend3.id AS personId,
        |    friend3.firstName AS firstName,
        |    friend3.lastName AS lastName
        |""".stripMargin
    val p = Map("personId" -> "62705")
    //预热
    //    MyGraph.run(q, p).show()
    //    val startTime1 = System.currentTimeMillis()
    //    MyGraph.run(q, p).show()
    //    System.out.println("程序运行时间： " + (System.currentTimeMillis() - startTime1) + "ms")
    MyGraph.load(0)
    val startTime = System.currentTimeMillis()
    MyGraph.run(q, p).show()
    System.out.println("程序运行时间： " + (System.currentTimeMillis() - startTime) + "ms")
//      val res = MyGraph.run(q, p)
//      println(res.records().length)
  }

  @Test
  def Q8(): Unit = {
    val q =
      """
        |MATCH p=(n1:person {id: $personId })-[:knows*4]-(n2:person)
        |RETURN
        |    p
        |""".stripMargin
    val p = Map("personId" -> "62705")
    //预热
    //    MyGraph.run(q, p).show()
    //    val startTime1 = System.currentTimeMillis()
    //    MyGraph.run(q, p).show()
    //    System.out.println("程序运行时间： " + (System.currentTimeMillis() - startTime1) + "ms")
    MyGraph.load(5000)
    val startTime = System.currentTimeMillis()
    MyGraph.run(q, p).show()
    System.out.println("程序运行时间： " + (System.currentTimeMillis() - startTime) + "ms")
  }

  @Test
  def Q10(): Unit = {
    val q =
      """
        |SELECT p.id, p.lastName, p.firstName
        |FROM Person_Knows_Person pkp1
        |JOIN person_knows_person pkp2
        |  ON pkp1.OtherPerson_id = pkp2.Person_id
        |JOIN person_knows_person pkp
        |  ON pkp2.OtherPerson_id = pkp.Person_id
        |JOIN person p
        |  ON p.id = pkp.OtherPerson_id
        |WHERE pkp1.Person_id = 62705
        |""".stripMargin
    val connection: Connection = DriverManager.getConnection(
      "jdbc:mysql://localhost:3307/ldbcsf10?serverTimezone=UTC&useUnicode=true&characterEncoding=utf8&useSSL=false", "root", "root"
    )
    val statement: Statement = connection.createStatement()
    val startTime = System.currentTimeMillis()
    val resultSet: ResultSet = statement.executeQuery(q)
    val endTime = System.currentTimeMillis()

    if (resultSet.next()) {
      println(s"Column 1: ${resultSet.getString(1)}")
      println(s"Column 2: ${resultSet.getString(3)}")
      println(s"Column 3: ${resultSet.getString(3)}")
    }
    System.out.println("程序运行时间： " + (endTime - startTime) + "ms")
  }

  @Test
  def pathSQL(): Unit = {
    val q =
      """
        |SELECT DISTINCT
        |    pkp1.Person_id AS startPersonId,
        |    pkp2.Person_id AS firstHopPersonId,
        |    pkp3.Person_id AS thirdHopPersonId
        |FROM
        |    person_knows_person pkp1
        |JOIN
        |    person_knows_person pkp2 ON pkp1.OtherPerson_id = pkp2.Person_id
        |JOIN
        |    person_knows_person pkp3 ON pkp2.OtherPerson_id = pkp3.Person_id
        |WHERE
        |    pkp1.Person_id = 62705;
        |""".stripMargin
    val connection: Connection = DriverManager.getConnection(
      "jdbc:mysql://localhost:3307/ldbcsf10?serverTimezone=UTC&useUnicode=true&characterEncoding=utf8&useSSL=false", "root", "root"
    )
    val statement: Statement = connection.createStatement()
    val startTime = System.currentTimeMillis()
    val resultSet: ResultSet = statement.executeQuery(q)
    val endTime = System.currentTimeMillis()
    var rowCount = 0
    while (resultSet.next()) {
      rowCount += 1
      //      println(s"${resultSet.getString(1)}|${resultSet.getString(2)}|${resultSet.getString(3)}")
    }
    println(rowCount)
    System.out.println("程序运行时间： " + (endTime - startTime) + "ms")
  }

  @Test
  def targetSQL(): Unit = {
    val q =
      """
        |SELECT p.id, p.lastName, p.firstName
        |FROM Person_Knows_Person pkp1
        |JOIN person_knows_person pkp2
        |  ON pkp1.OtherPerson_id = pkp2.Person_id
        |JOIN person_knows_person pkp
        |  ON pkp2.OtherPerson_id = pkp.Person_id
        |JOIN person p
        |  ON p.id = pkp.OtherPerson_id
        |WHERE pkp1.Person_id = 62705
        |GROUP BY p.id, p.lastName, p.firstName
        |
        |""".stripMargin
    val connection: Connection = DriverManager.getConnection(
      "jdbc:mysql://localhost:3307/ldbcsf10?serverTimezone=UTC&useUnicode=true&characterEncoding=utf8&useSSL=false", "root", "root"
    )
    val statement: Statement = connection.createStatement()
    val startTime = System.currentTimeMillis()
    val resultSet: ResultSet = statement.executeQuery(q)
    val endTime = System.currentTimeMillis()
    var rowCount = 0
    while (resultSet.next()) {
      rowCount += 1
      //      println(s"${resultSet.getString(1)}|${resultSet.getString(2)}|${resultSet.getString(3)}")
    }
    println(rowCount)
    System.out.println("程序运行时间： " + (endTime - startTime) + "ms")
  }
}
