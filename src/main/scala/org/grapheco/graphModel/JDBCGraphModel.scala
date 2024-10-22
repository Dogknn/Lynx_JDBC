package graphModel

import LynxJDBCElement._
import LynxJDBCElement.Mapper.mapRel
import com.typesafe.scalalogging.LazyLogging
import org.grapheco.lynx.physical._
import org.grapheco.lynx.runner.{GraphModel, NodeFilter, RelationshipFilter, WriteTask}
import org.grapheco.lynx.types.LynxValue
import org.grapheco.lynx.types.structural._
import org.opencypher.v9_0.expressions.SemanticDirection
import org.scalacheck.Gen.{const, size}
import schema._

import java.sql.{Connection, ResultSet, SQLException}
import scala.Iterator

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
    try {
      logger.info(sql)
      val statement = connection.createStatement()
      val result = statement.executeQuery(sql)
      Iterator.continually(result).takeWhile(_.next())
      //      new ResultSetIterator(result)
    } catch {
      case e: SQLException =>
        e.printStackTrace()
        Iterator.empty
    }
  }

  private def singleTableSelect(tableName: String, filters: Map[LynxPropertyKey, LynxValue]): Iterator[ResultSet] = {
    val conditions: Map[String, Any] = filters.map { case (k, v) => k.toString -> v.value }
    //    mulityTableSelect()
    singleTableSelect(tableName, conditions.toList)
  }

  private def singleTableSelect(tableName: String, condition: Seq[(String, Any)]): Iterator[ResultSet] = {
    val singleTable = schema.getTableByName(tableName)
    singleTable match {
      case RDBTable.empty => Iterator.empty
      case t: RDBTable =>
        val sql = s"select * from ${t.tableName} ${
          if (condition.isEmpty) ""
          else "where " + condition.map {
            case (k, v) => s"$k = $v"
          }.mkString("and")
        }"
        excuteQuery(sql)
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

  //  /**
  //   *
  //   * @param nodeId
  //   * @param filter
  //   * @param direction
  //   * @return
  //   */
  //  override private def expand(nodeId: LynxId, filter: RelationshipFilter, direction: SemanticDirection): Iterator[PathTriple] = {
  //    filter.types.size match {
  //      //      case 0 =>
  //      case 1 => expand(nodeId, filter.types.headOption.get, direction)
  //      //      case _ =>
  //    }
  //  }

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
    val nodeTable = schema.getNodeByNodeLabel(nodeLabel)
    //    val nodeTable = node.
    val rel_Type = filter.types.head.value
    val rel = schema.getReltionshipByType(rel_Type)
    val rel_Condition = filter.properties.map {
      case (k, v) => s"${k.value} = ${v.value}"
    }
    //      schema.getRel_Node(nodeLabel, rel_Type, direction)
    if (rel == GraphRelationship.empty)
      return Iterator.empty

    val rel_ColName = rel.direction match {
      case _ => nodeTable.tableName match {
        case rel.sourceTableName => rel.sourceCol
        case rel.targetTableName => rel.targetCol
      }
      //      case _ => rel.sourceTableName match {
      //        case nodeTable.tableName => rel.sourceCol
      //        case nodeTable.tableName => rel.targetCol
      //      }
    }

    val whereSql = (direction match {
      case SemanticDirection.OUTGOING => s"where ${rel.bindingTable}.$rel_ColName = ${nodeId.toLynxInteger.value}"
      case SemanticDirection.INCOMING => s"where ${rel.bindingTable}.$rel_ColName = ${nodeId.toLynxInteger.value}"
    }) + (if (rel_Condition.nonEmpty) " and " + rel_Condition.mkString(" and ") else "")
    val sql = s"select * from ${rel.bindingTable} $whereSql"
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
    upperLimit match {
      case 0 => singlePath(startNodeFilter, relationshipFilter, endNodeFilter, direction)
      case _ => mulityPath(startNodeFilter, relationshipFilter, endNodeFilter, direction, upperLimit, lowerLimit)
    }


  }

  private def singlePath(startNodeFilter: NodeFilter,
                         relationshipFilter: RelationshipFilter,
                         endNodeFilter: NodeFilter,
                         direction: SemanticDirection): Iterator[LynxPath] = {

    val pathTriple = (startNodeFilter.labels.headOption.getOrElse(LynxNodeLabel(null)),
      relationshipFilter.types.headOption.getOrElse(LynxRelationshipType(null)),
      endNodeFilter.labels.headOption.getOrElse(LynxNodeLabel(null)))
    val rel = schema.getReltionshipByType(relationshipFilter.types.headOption.getOrElse(LynxRelationshipType(null)).value)
    // TODO 三元组先匹配类型
    val node_1 = schema.getNodeByNodeLabel(startNodeFilter.labels.headOption.getOrElse(LynxNodeLabel(null)).value)

    val node_2 = schema.getNodeByNodeLabel(startNodeFilter.labels.headOption.getOrElse(LynxNodeLabel(null)).value)

    return Iterator.empty
  }

  private def mulityPath(startNodeFilter: NodeFilter,
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

  def node(nodeFilter: NodeFilter): Iterator[LynxJDBCNode] = {
    if (nodeFilter.labels.isEmpty && nodeFilter.properties.isEmpty) return nodes()
    val node_Label = nodeFilter.labels.head.toString
    schema.getNodeByNodeLabel(node_Label) match {
      case GraphNode.empty => return Iterator.empty
      case v => singleTableSelect(node_Label, nodeFilter.properties).map(rs =>
        Mapper.mapNode(rs, v.tableName, schema)
      )
    }
  }

  //  override def nodes(): Iterator[LynxJDBCNode] = {
  //    schema.gNodes.toIterator.map {
  //      node =>
  //        node(NodeFilter(
  //          Seq(LynxNodeLabel(node.nodeLabel)),
  //          schema.getNodeByNodeLabel(node.nodeLabel).properties.map(v
  //          => LynxPropertyKey(v.columnName) -> LynxValue(v.dataType)).toMap))
  //    }
  //  }
  //    schema.gNodes.toIterator.flatMap { node =>
  //    nodes(NodeFilter(labels = Seq(LynxNodeLabel(node.nodeLabel)), properties = Map.empty))
  //  }

  override def nodes(): Iterator[LynxJDBCNode] = schema.gNodes.toIterator.flatMap { node =>
    val labels = Seq(LynxNodeLabel(node.nodeLabel))
    nodes(NodeFilter(labels, Map.empty))
  }

  override def nodes(nodeFilter: NodeFilter): Iterator[LynxJDBCNode] = {
    if (nodeFilter.labels.isEmpty && nodeFilter.properties.isEmpty) return nodes()
    val node_Label = nodeFilter.labels.head.toString
    schema.getNodeByNodeLabel(node_Label) match {
      case GraphNode.empty => return Iterator.empty
      case v => singleTableSelect(node_Label, nodeFilter.properties).map(rs =>
        Mapper.mapNode(rs, v.tableName, schema)
      )
    }
  }

  override def relationships(): Iterator[PathTriple] = ???
}
