package schema

import org.opencypher.v9_0.expressions.SemanticDirection
import play.api.libs.json._
import schema.GraphRelationship.semanticDirectionReads
import schema.element.{BaseProperty, FKProperty, PKProperty, Property}

import java.io.{File, PrintWriter}
import java.nio.file.Files
import java.sql.{Connection, DatabaseMetaData}
import scala.language.postfixOps

object SchemaManager {

  def readJson(filePath: String): Schema = {
    val fileContent = new String(Files.readAllBytes(new File(filePath).toPath))
    val json = Json.parse(fileContent)

    val tables = (json \ "RDBTables").get.asInstanceOf[JsArray].value.map { table =>
      RDBTable((table \ "tableName").as[String],
        (table \ "priKeyCol").as[PKProperty],
        (table \ "forKeyCol").as[Array[FKProperty]],
        (table \ "tableType").as[String],
        (table \ "properties").as[Array[BaseProperty]])
    }

    val gNodes = (json \ "Nodes").validate[Seq[GraphNode]].asOpt.getOrElse(Seq.empty[GraphNode])

    val gRelationship = (json \ "Relationships").get.asInstanceOf[JsArray].value.map { relationship =>
      GraphRelationship((relationship \ "relationshipType").as[String],
        (relationship \ "sourceTableName").as[String],
        processProperty(relationship \ "sourceCol"),
        (relationship \ "direction").as[SemanticDirection],
        (relationship \ "targetTableName").as[String],
        processProperty(relationship \ "targetCol"),
        (relationship \ "bindingTable").as[String])
    }
    Schema(tables, gNodes, gRelationship)
    //    Schema(processSeqTables(tables), gNodes, processSeqRelationship(gRelationship))
  }

  //  def processSeqTables(tables: Any): Seq[RDBTable] = tables match {
  //    case v: Seq[RDBTable] => v
  //    case _ => Seq.empty
  //  }
  //
  //  def processSeqRelationship(relationships: Any): Seq[GraphRelationship] = relationships match {
  //    case v: Seq[GraphRelationship] => v
  //    case _ => Seq.empty
  //  }

  def processProperty(prop: JsLookupResult): Property = (prop.get.toString().contains("referenceTableName"), prop.get.toString().contains("nodeLabel")) match {
    case (false, false) => prop.as[Property]
    case (false, _) => prop.as[PKProperty]
    case (_, false) => prop.as[FKProperty]
  }

  private def writeJson(filePath: String, schema: Schema): Unit = {

    if (schema.tables.contains(null) || schema.gNodes.contains(null) || schema.gRelationship.contains(null)) {
      throw new IllegalArgumentException("Schema contains null values")
    }

    val jsonContext = Json.prettyPrint(
      Json.obj(
        "RDBTables" -> Json.toJson(schema.tables),
        "Nodes" -> Json.toJson(schema.gNodes),
        "Relationships" -> Json.toJson(schema.gRelationship)
      )
    )
    val jsonPath = if (filePath.isEmpty) "schema.json" else filePath
    val writer = new PrintWriter(jsonPath)
    writer.write(jsonContext)
    writer.close()
  }

  def saveJson(filePath: String, schema: Schema): Unit = {
    writeJson(filePath, schema)
  }


  def autoGeneration(connection: Connection): Schema = {
    val databaseMetaData = connection.getMetaData
    val catalog = connection.getCatalog

    var graphNodes = Array[GraphNode]()
    var graphRelationships = Array[GraphRelationship]()
    var rdbTables = Array[RDBTable]()

    val tablesRS = databaseMetaData.getTables(catalog, null, null, null)

    while (tablesRS.next()) {
      val tableName = tablesRS.getString("TABLE_NAME")
      val rdbTable = processTable(databaseMetaData, catalog, tableName)
      rdbTables :+= rdbTable
      rdbTable.tableType match {
        case "NODE" => graphNodes :+= processNode(rdbTable)
        case "RELATIONSHIP" => graphRelationships = graphRelationships ++ processREL(rdbTable)
        case "BOTH" => graphNodes :+= processNode(rdbTable)
          graphRelationships = graphRelationships ++ processBOTH(rdbTable)
      }
    }
    Schema(rdbTables, graphNodes, graphRelationships)
  }

  private def processNode(graphTable: RDBTable): GraphNode = {
    graphTable.priKeyCol match {
      case PKProperty.empty => return GraphNode.empty
      case _ => GraphNode(graphTable.tableName, graphTable.tableName, graphTable.tableName)
    }
  }

  private def processREL(graphTable: RDBTable): Array[GraphRelationship] = {
    var graphRelationships = Array[GraphRelationship]()
    val fkArr = graphTable.forKeyCol
    for {
      (fk, index) <- fkArr.zipWithIndex
      i <- index + 1 until fkArr.length
    } {
      graphRelationships :+= GraphRelationship(graphTable.tableName, fk.referenceTableName, fk, SemanticDirection.OUTGOING, fkArr(i).referenceTableName, fkArr(i), graphTable.tableName)
    }
    graphRelationships
  }

  private def processBOTH(graphTable: RDBTable): Array[GraphRelationship] = {
    var graphRelationships = Array[GraphRelationship]()
    val fkArr = graphTable.forKeyCol
    for (i <- 0 until fkArr.length) {
      graphRelationships :+= GraphRelationship(graphTable.tableName, graphTable.tableName, graphTable.priKeyCol, SemanticDirection.OUTGOING, fkArr(i).referenceTableName, fkArr(i), graphTable.tableName)
    }
    fkArr.size match {
      case 1 => graphRelationships
      case _ => graphRelationships ++ processREL(graphTable)
    }

  }

  private def processTable(databaseMetaData: DatabaseMetaData, catalog: String, tableName: String): RDBTable = {

    var fkArray = Array.empty[FKProperty]
    var genPropArr = Array.empty[BaseProperty]
    var genericPropertyMap = Map.empty[String, String]

    val propResultSet = databaseMetaData.getColumns(catalog, null, tableName, null)
    while (propResultSet.next()) {
      val columnName = propResultSet.getString("COLUMN_NAME")
      val columnType = propResultSet.getString("TYPE_NAME")
      genericPropertyMap += (columnName -> columnType)
      if (!columnName.contains(":")) genPropArr :+= BaseProperty(columnName, columnType)
    }

    val foreignKeyResultSet = databaseMetaData.getImportedKeys(catalog, null, tableName)
    val metaData = foreignKeyResultSet.getMetaData

    //    for (i <- 1 to metaData.getColumnCount) {
    //      println(metaData.getColumnName(i))
    //    }
    //    if (!foreignKeyResultSet.first()) fkArray :+= FKProperty(null, null, null, null) else


    val primaryKeyResultSet = databaseMetaData.getPrimaryKeys(catalog, null, tableName)

    val pkprop = primaryKeyResultSet.next() match {
      case false => PKProperty.empty
      case _ => PKProperty(primaryKeyResultSet.getString("COLUMN_NAME"),
        genericPropertyMap(primaryKeyResultSet.getString("COLUMN_NAME")) match {
          case v: String => v
        }, tableName)
    }

    val tableType = ((pkprop == PKProperty.empty), (fkArray.length > 0)) match {
      case (false, true) => "BOTH"
      case (false, false) => "NODE"
      case _ => "RELATIONSHIP"
      //TODO
      //   case (true, true) => "RELATIONSHIP"
      //   case (ture, false) => "NODE"
    }

    while (foreignKeyResultSet.next()) {
      val (colName, colType, fkTableName, fkTableColName) = (
        foreignKeyResultSet.getString("FKCOLUMN_NAME"),
        //        foreignKeyResultSet.getString("TYPE_NAME"),
        genericPropertyMap(foreignKeyResultSet.getString("FKCOLUMN_NAME")) match {
          case v: String => v
        },
        foreignKeyResultSet.getString("PKTABLE_NAME"),
        foreignKeyResultSet.getString("PKCOLUMN_NAME")
      )
      fkArray :+= FKProperty(colName, colType, fkTableName, fkTableColName)
    }

//    if (pkprop == PKProperty.empty) println("alert")
    if (fkArray.isEmpty) fkArray :+= FKProperty.empty
    RDBTable(tableName, pkprop, fkArray, tableType, genPropArr)
  }
}
