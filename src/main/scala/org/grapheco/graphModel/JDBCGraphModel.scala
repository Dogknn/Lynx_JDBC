package graphModel

import LynxJDBCElement.{LynxJDBCNode, Mapper}
import LynxJDBCElement.Mapper.mapRel
import com.typesafe.scalalogging.LazyLogging
import org.grapheco.lynx.physical.{NodeInput, RelationshipInput}
import org.grapheco.lynx.runner.{GraphModel, NodeFilter, RelationshipFilter, WriteTask}
import org.grapheco.lynx.types.LynxValue
import org.grapheco.lynx.types.structural.{LynxId, LynxNode, LynxNodeLabel, LynxPath, LynxPropertyKey, LynxRelationship, LynxRelationshipType, PathTriple}
import org.opencypher.v9_0.expressions.SemanticDirection
import schema._

import java.sql.{Connection, ResultSet, SQLException}
import scala.Iterator
import scala.collection.Seq

class JDBCGraphModel(val connection: Connection, val schema: Schema) extends GraphModel with LazyLogging {
  override def write: WriteTask = new WriteTask {
    override def createElements[T](nodesInput: Seq[(String, NodeInput)], relationshipsInput: Seq[(String, RelationshipInput)], onCreated: (Seq[(String, LynxNode)], Seq[(String, LynxRelationship)]) => T): T = ???

    override def deleteRelations(ids: Iterator[LynxId]): Unit = ???

    override def deleteNodes(ids: Seq[LynxId]): Unit = ???

    override def updateNode(lynxId: LynxId, labels: Seq[LynxNodeLabel], props: Map[LynxPropertyKey, LynxValue]): Option[LynxNode] = ???

    override def updateRelationShip(lynxId: LynxId, props: Map[LynxPropertyKey, LynxValue]): Option[LynxRelationship] = ???

    override def setNodesProperties(nodeIds: Iterator[LynxId], data: Array[(LynxPropertyKey, Any)], cleanExistProperties: Boolean): Iterator[Option[LynxNode]] = ???

    override def setNodesLabels(nodeIds: Iterator[LynxId], labels: Array[LynxNodeLabel]): Iterator[Option[LynxNode]] = ???

    override def setRelationshipsProperties(relationshipIds: Iterator[LynxId], data: Array[(LynxPropertyKey, Any)]): Iterator[Option[LynxRelationship]] = ???

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

  //  class ResultSetIterator(resultSet: ResultSet) extends AbstractIterator[ResultSet] {
  //    override def hasNext: Boolean = resultSet.next()
  //    override def next(): ResultSet = resultSet
  //  }
  private def excute(sql: String): Iterator[ResultSet] = {
    logger.info(sql)
    val statement = connection.createStatement()
    val result = statement.executeQuery(sql)
    Iterator.continually(result).takeWhile(_.next())
  }

  private def excuteQuery(sql: String): Iterator[ResultSet] = {
//    try {
      logger.info(sql)
      val statement = connection.createStatement()
      val result = statement.executeQuery(sql)
      Iterator.continually(result).takeWhile(_.next())
//      //      new ResultSetIterator(result)
//      //  class ResultSetIterator(resultSet: ResultSet) extends AbstractIterator[ResultSet] {
//      //    override def hasNext: Boolean = resultSet.next()
//      //    override def next(): ResultSet = resultSet
//      //  }
//    } catch {
//      case e: SQLException =>
//        e.printStackTrace()
//        Iterator.empty
//    }
  }

  private def iterExecute(sql: String): Iterator[ResultSet] = {
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
//        println(sql)
        excuteQuery(sql)
        println( excuteQuery(sql).toSeq.head.toString)
        excuteQuery(sql)
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
    //    val nodeTable = node.
    val rel_Type = filter.types.head.value
    val rel_Condition = filter.properties.map {
      case (k, v) => s"${k.value} = ${v.value}"
    }
    val rel_Node = ""
    //      schema.getRel_Node(nodeLabel, rel_Type, direction)
    if (rel_Node.isEmpty)
      return Iterator.empty

    //    val selectWhat = schema.triples
    val whereSql = (direction match {
      case SemanticDirection.OUTGOING => s"where $rel_Type.$rel_Node = ${nodeId.toLynxInteger.value}"
      case SemanticDirection.INCOMING => s"where $rel_Type.$rel_Node = ${nodeId.toLynxInteger.value}"
    }) + (if (rel_Condition.nonEmpty) " and " + rel_Condition.mkString(" and ") else "")
    val sql = s"select * from $whereSql"
    excute(sql).map { rs =>
      val endNode = direction match {
        case _ =>
      }
      PathTriple(node, mapRel(rs, "", schema), node)
    }
  }

  private def nodeAt(id: LynxId, tableName: String): Option[LynxNode] = {
    val nodeId = schema.getTableByName(tableName).priKeyCol.columnName
    singleTableSelect(tableName, List((nodeId, id.toLynxInteger.v)))
      .map(rs => Mapper.mapNode(rs, tableName, schema)).toSeq.headOption
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
    }.filter(_.endNode.forall(endNodeFilter.matches))
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
