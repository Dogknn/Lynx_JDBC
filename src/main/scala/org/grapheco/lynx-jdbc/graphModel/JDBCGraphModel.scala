package graphModel

import LynxJDBCElement.{LynxIntegerID, LynxJDBCNode, LynxJDBCRelationship, Mapper}
import LynxJDBCElement.Mapper.mapRel
import com.typesafe.scalalogging.LazyLogging
import org.grapheco.lynx.types.structural.LynxId
import org.grapheco.lynx.physical.{NodeInput, RelationshipInput}
import org.grapheco.lynx.runner.{GraphModel, NodeFilter, RelationshipFilter, WriteTask}
import org.grapheco.lynx.types.LynxValue
import org.grapheco.lynx.types.structural.{LynxNode, LynxNodeLabel, LynxPath, LynxPropertyKey, LynxRelationship, LynxRelationshipType, PathTriple}
import org.opencypher.v9_0.expressions.SemanticDirection
import schema._

import java.sql.{Connection, ResultSet}

class JDBCGraphModel(val connection: Connection, val schema: Schema) extends GraphModel with LazyLogging {
  override def write: WriteTask = new WriteTask {
    override def createElements[T](nodesInput: Seq[(String, NodeInput)], relationshipsInput: Seq[(String, RelationshipInput)], onCreated: (Seq[(String, LynxNode)], Seq[(String, LynxRelationship)]) => T): T = ???

    override def deleteRelations(ids: Iterator[LynxId]): Unit = ???

    override def deleteNodes(ids: Seq[LynxId]): Unit = ???

    override def updateNode(lynxId: LynxId, labels: Seq[LynxNodeLabel], props: Map[LynxPropertyKey, LynxValue]): Option[LynxNode] = ???

    override def updateRelationShip(lynxId: LynxId, props: Map[LynxPropertyKey, LynxValue]): Option[LynxRelationship] = ???

    override def setNodesProperties(nodeIds: Iterator[LynxId], data: Array[(LynxPropertyKey, LynxValue)], cleanExistProperties: Boolean): Iterator[Option[LynxNode]] = ???

    override def setNodesLabels(nodeIds: Iterator[LynxId], labels: Array[LynxNodeLabel]): Iterator[Option[LynxNode]] = ???

    override def setRelationshipsProperties(relationshipIds: Iterator[LynxId], data: Array[(LynxPropertyKey, LynxValue)], cleanExistProperties: Boolean): Iterator[Option[LynxRelationship]] = ???

    override def setRelationshipsType(relationshipIds: Iterator[LynxId], typeName: LynxRelationshipType): Iterator[Option[LynxRelationship]] = ???

    override def removeNodesProperties(nodeIds: Iterator[LynxId], data: Array[LynxPropertyKey]): Iterator[Option[LynxNode]] = ???

    override def removeNodesLabels(nodeIds: Iterator[LynxId], labels: Array[LynxNodeLabel]): Iterator[Option[LynxNode]] = ???

    override def removeRelationshipsProperties(relationshipIds: Iterator[LynxId], data: Array[LynxPropertyKey]): Iterator[Option[LynxRelationship]] = ???

    override def removeRelationshipsType(relationshipIds: Iterator[LynxId], typeName: LynxRelationshipType): Iterator[Option[LynxRelationship]] = ???

    override def commit: Boolean = true
  }

  override def nodeAt(id: LynxId): Option[LynxNode] = {
    val nameList: Seq[String] = schema.tables.map(_.tableName)
    nodeAt(id, nameList)
  }

  private def execute(sql: String): Iterator[ResultSet] = {
    logger.info(sql)
    val statement = connection.createStatement()
    val result = statement.executeQuery(sql)
    Iterator.continually(result).takeWhile(_.next())
  }

  private def executeQuery(sql: String): Iterator[ResultSet] = {
    logger.info(sql)
    val statement = connection.createStatement()
    val result = statement.executeQuery(sql)
    Iterator.continually(result).takeWhile(_.next())
  }

  private def singleTableSelect(tableName: String, condition: Seq[(String, Any)]): Iterator[ResultSet] = {
    val singleTable = schema.getTableByName(tableName)
    singleTable match {
      case t: RDBTable =>
        val sql = s"select * from ${t.tableName} ${
          if (condition.isEmpty) ""
          else "where " + condition.map {
            case (k, v) => s"$k = $v"
          }.mkString("and")
        }"
        executeQuery(sql)
      case RDBTable.empty => Iterator.empty
    }
  }

  //  private def mutilTableSelect(tableName: String, idList: Seq[(String, Any)]): Iterator[ResultSet] = {
  //    val singleTable = schema.getTableByName(tableName)
  //    singleTable match {
  //      case t: GraphTable =>
  //        val sql = s"select * from ${t.tableName} ${
  //          if (condition.isEmpty) ""
  //          else "where" + condition.map {
  //            case (k, v) => s"$k = $v"
  //          }.mkString("and")
  //        }"
  //        excuteQuery(sql)
  //      case GraphTable.empty => Iterator.empty
  //    }
  //  }

  /**
   *
   * @param nodeId
   * @param filter
   * @param direction
   * @return
   */
  override def expand(nodeId: LynxId, filter: RelationshipFilter, direction: SemanticDirection): Iterator[PathTriple] = {
    filter.types.size match {
      case 0 => Iterator()
      case 1 => Iterator()
      //          expand(nodeId, filter.types.headOption.get, direction)
      //      case _ =>
    }
  }

  override def extendPath(path: LynxPath, relationshipFilter: RelationshipFilter, direction: SemanticDirection, steps: Int): Iterator[LynxPath] = {
    if (path.isEmpty || steps <= 0) return Iterator(path)
    Iterator(path) ++
      expand(path.endNode.get, relationshipFilter, direction)
        .filterNot(tri => path.nodeIds.contains(tri.endNode.id))
        .map(_.toLynxPath)
        .map(_.connectLeft(path)).flatMap(p => extendPath(p, relationshipFilter, direction, steps - 1))
  }

  private def expand(node: LynxNode, filter: RelationshipFilter, direction: SemanticDirection): Iterator[PathTriple] = {
    val nodeId = node.id
    val nodeLabel = node.labels.head.value
    val rel_Type = filter.types.head.value
    val rel_Condition = filter.properties.map {
      case (k, v) => s"${k.value} = ${v.value}"
    }
    val relTable = schema.getReltionshipByType(rel_Type)
    val whereSql = (direction match {
      case SemanticDirection.OUTGOING => s"where ${relTable.bindingTable}.${relTable.sourceCol.columnName} = ${nodeId.toLynxInteger.value}"
      case SemanticDirection.INCOMING => s"where ${relTable.bindingTable}.${relTable.targetCol.columnName} = ${nodeId.toLynxInteger.value}"
      case SemanticDirection.BOTH => s"where ${relTable.bindingTable}.${relTable.sourceCol.columnName} = ${nodeId.toLynxInteger.value}"
    }) + (if (rel_Condition.nonEmpty) " and " + rel_Condition.mkString(" and ") else "")
    val sql = s"select * from ${relTable.bindingTable} $whereSql"

        execute(sql).map { rs =>
          val endNode = direction match {
            case SemanticDirection.OUTGOING => nodeAt(LynxIntegerID(rs.getLong(relTable.sourceCol.columnName)), relTable.targetTableName)
            case SemanticDirection.INCOMING => nodeAt(LynxIntegerID(rs.getLong(relTable.targetCol.columnName)), relTable.targetTableName)
            case SemanticDirection.BOTH => nodeAt(LynxIntegerID(rs.getLong(relTable.targetCol.columnName)), relTable.targetTableName)
          }
          PathTriple(node, mapRel(rs, relTable.bindingTable, schema), endNode.get)
        }

//    // select in
//    val (ids, endTable) = direction match {
//      case SemanticDirection.OUTGOING => (execute(sql).map { rs => LynxIntegerID(rs.getLong(relTable.sourceCol.columnName)) }, relTable.sourceTableName)
//      case SemanticDirection.INCOMING => (execute(sql).map { rs => LynxIntegerID(rs.getLong(relTable.targetCol.columnName)) }, relTable.targetTableName)
//      case SemanticDirection.BOTH => (execute(sql).map { rs => LynxIntegerID(rs.getLong(relTable.targetCol.columnName)) }, relTable.targetTableName)
//    }
//    nodesAt(ids, endTable).map(en => PathTriple(node,
//      LynxJDBCRelationship(
//        LynxIntegerID(nodeId.toLynxInteger.value),
//        LynxIntegerID(nodeId.toLynxInteger.value),
//        LynxIntegerID(en.id.toLynxInteger.value),
//        Some(LynxRelationshipType(rel_Type)),
//        schema.getTableByName(relTable.bindingTable).properties.map(prop => (LynxPropertyKey(prop.columnName), LynxValue(prop.dataType))).toMap
//      ), en))

  }


  private def nodeAt(id: LynxId, tableName: String): Option[LynxNode] = {
    val nodeId = schema.getTableByName(tableName).priKeyCol.columnName
    singleTableSelect(tableName, List((nodeId, id.toLynxInteger.v)))
      .map(rs => Mapper.mapNode(rs, tableName, schema)).toSeq.headOption
  }

  private def nodesAt(ids: Iterator[LynxId], tableName: String): Iterator[LynxNode] = {
    val table = schema.getTableByName(tableName)
    table match {
      case RDBTable.empty =>
        Iterator.empty
      case t: RDBTable =>
        val idCondition = ids.map(id => s"id = ${id.value}").mkString(" or ")
        val sql = s"select * from ${t.tableName} where $idCondition"
        executeQuery(sql).map(rs => Mapper.mapNode(rs, tableName, schema))
    }
  }


  private def nodeAt(id: LynxId, nameList: Seq[String]): Option[LynxNode] = {
    nameList.flatMap { t =>
      val nodeId = schema.getTableByName(t).priKeyCol.columnName
      singleTableSelect(t, List((nodeId, id.toLynxInteger.v)))
        .map(rs =>
          Mapper.mapNode(rs, t, schema)).toSeq.headOption
    }.headOption
  }

  override def expandNonStop(start: LynxNode, relationshipFilter: RelationshipFilter, direction: SemanticDirection, steps: Int): Iterator[LynxPath] = {
    if (steps < 0) return Iterator(LynxPath.EMPTY)
    if (steps == 0) return Iterator(LynxPath.startPoint(start))
    //    expand(start.id, relationshipFilter, direction).flatMap{ triple =>
    //      expandNonStop(triple.endNode, relationshipFilter, direction, steps - 1).map{_.connectLeft(triple.toLynxPath)}
    //    }
    // TODO check cycle
    expand(start, relationshipFilter, direction)
      .flatMap { triple =>
        expandNonStop(triple.endNode, relationshipFilter, direction, steps - 1)
          .filterNot(_.nodeIds.contains(triple.startNode.id))
          .map {
            _.connectLeft(triple.toLynxPath)
          }
      }
  }


  override def paths(startNodeFilter: NodeFilter,
                     relationshipFilter: RelationshipFilter,
                     endNodeFilter: NodeFilter,
                     direction: SemanticDirection,
                     upperLimit: Int,
                     lowerLimit: Int): Iterator[LynxPath] = {
    val originStations = nodes(startNodeFilter)
    originStations.flatMap { originStation =>
      val firstStop = expandNonStop(originStation, relationshipFilter, direction, lowerLimit)
      val leftSteps = Math.min(upperLimit, 100) - lowerLimit // TODO set a super upperLimit
      firstStop.flatMap(p => extendPath(p, relationshipFilter, direction, leftSteps))
    }.filter(_.endNode.forall(endNode => endNodeFilter.matches(endNode)))
  }

  override def nodes(): Iterator[LynxJDBCNode] =
    schema.gNodes.toIterator.flatMap { node =>
      nodes(NodeFilter(Seq(LynxNodeLabel(node.nodeLabel)), Map.empty))
    }

  override def nodes(nodeFilter: NodeFilter): Iterator[LynxJDBCNode] = {
    if (nodeFilter.labels.isEmpty && nodeFilter.properties.isEmpty) return nodes()
    val label = nodeFilter.labels.head.toString
    val filteredSchema = schema.getNodeByNodeLabel(label)
    filteredSchema match {
      case fs =>
        singleTableSelect(label, nodeFilter.properties.map(f => (f._1.value, f._2.value)).toSeq).map { rs =>
          Mapper.mapNode(rs, fs.tableName, schema)
        }
      case _ =>
        Iterator.empty
    }
  }


  override def relationships(): Iterator[PathTriple] = {
    schema.gRelationship.flatMap { rel =>
      val t = LynxRelationshipType(rel.bindingTable)
      relationships(RelationshipFilter(Seq(t), Map.empty))
    }.toIterator
  }

}
