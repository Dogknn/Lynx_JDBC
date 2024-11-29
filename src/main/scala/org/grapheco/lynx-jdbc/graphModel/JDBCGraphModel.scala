package graphModel

import LynxJDBCElement.{LynxIntegerID, LynxJDBCNode, LynxJDBCRelationship, Mapper}
import LynxJDBCElement.Mapper.{mapNode, mapRel}
import com.github.benmanes.caffeine.cache.{Cache, Caffeine}
import com.typesafe.scalalogging.LazyLogging
import org.grapheco.lynx.types.structural.{LynxElement, LynxId, LynxNode, LynxNodeLabel, LynxPath, LynxPropertyKey, LynxRelationship, LynxRelationshipType, PathTriple}
import org.grapheco.lynx.physical.{NodeInput, RelationshipInput}
import org.grapheco.lynx.runner.{GraphModel, NodeFilter, RelationshipFilter, WriteTask}
import org.grapheco.lynx.types.LynxValue
import org.opencypher.v9_0.expressions.SemanticDirection
import schema._

import java.sql.{Connection, ResultSet}
import java.util.concurrent.{ForkJoinPool, TimeUnit}
import scala.collection.compat.toMutableQueueExtensionMethods
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.collection.parallel.ForkJoinTaskSupport

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


  private val queryCache: Cache[String, Seq[ResultSet]] = Caffeine.newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(10, TimeUnit.MINUTES)
    .build()

  private def executeQuery(sql: String): Iterator[ResultSet] = {
    logger.info(sql)

    val cachedResult = queryCache.getIfPresent(sql)
    if (cachedResult != null) {
      logger.info(s"Cache hit for SQL: $sql")
      return cachedResult.iterator
    }

    logger.info(s"Cache miss for SQL: $sql. Executing query.")
    val statement = connection.createStatement()
    val resultSet = statement.executeQuery(sql)
    val result = Iterator.continually(resultSet).takeWhile(_.next()).toSeq

    queryCache.put(sql, result)
    result.iterator
  }

  private val GolbalCache: mutable.Map[LynxId, Seq[LynxId]] = mutable.Map()

  def loadTableData(i: Int): Unit = {
    val query =
      s"""
         |SELECT p.Person_id, p.OtherPerson_id
         |FROM person_knows_person p
         |JOIN (
         |  SELECT Person_id
         |  FROM person_knows_person
         |  GROUP BY Person_id
         |  ORDER BY COUNT(*) DESC
         |  LIMIT $i
         |) AS top_persons ON p.Person_id = top_persons.Person_id;
         |
         |""".stripMargin

    val resultSetIterator = execute(query)
    resultSetIterator.foreach { resultSet =>
      val personId = LynxIntegerID(resultSet.getLong("Person_id"))
      val otherPersonId = LynxIntegerID(resultSet.getLong("OtherPerson_id"))

      val currentNeighbors = GolbalCache.getOrElse(personId, Seq())
      GolbalCache.update(personId, currentNeighbors :+ otherPersonId)
    }
  }

  private def execute(sql: String): Iterator[ResultSet] = {
    logger.info(sql)
    val statement = connection.createStatement()
    val result = statement.executeQuery(sql)
    Iterator.continually(result).takeWhile(_.next())
  }

  //  private def executeQuery(sql: String): Iterator[ResultSet] = {
  //    logger.info(sql)
  //    val statement = connection.createStatement()
  //    val result = statement.executeQuery(sql)
  //    Iterator.continually(result).takeWhile(_.next())
  //  }

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
  //  override def expand(nodeId: LynxId, filter: RelationshipFilter, direction: SemanticDirection): Iterator[PathTriple] = {
  //    filter.types.size match {
  //      case 0 => Iterator()
  //      case 1 => Iterator()
  //      //          expand(nodeId, filter.types.headOption.get, direction)
  //      //      case _ =>
  //    }
  //  }

  //  override def extendPath(path: LynxPath, relationshipFilter: RelationshipFilter, direction: SemanticDirection, steps: Int): Iterator[LynxPath] = {
  //    if (path.isEmpty || steps <= 0) return Iterator(path)
  //    Iterator(path) ++
  //      expand(path.endNode.get, relationshipFilter, direction)
  //        .filterNot(tri => path.nodeIds.contains(tri.endNode.id))
  //        .map(_.toLynxPath)
  //        .map(_.connectLeft(path)).flatMap(p => extendPath(p, relationshipFilter, direction, steps - 1))
  //  }


  override def extendPath(path: LynxPath, relationshipFilter: RelationshipFilter, direction: SemanticDirection, steps: Int): Iterator[LynxPath] = {
    if (path.isEmpty || steps <= 0) return Iterator(path)
    expand(path.endNode.get, relationshipFilter, direction)
      .filterNot(tri => path.nodeIds.contains(tri.endNode.id))
      .map(tri => {
        val newPath = path.connect(tri.toLynxPath)
        newPath
      }).flatMap(newPath => extendPath(newPath, relationshipFilter, direction, steps - 1))
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
      case SemanticDirection.BOTH => s"where ${relTable.bindingTable}.${relTable.targetCol.columnName} = ${nodeId.toLynxInteger.value}"
    }) + (if (rel_Condition.nonEmpty) " and " + rel_Condition.mkString(" and ") else "")
    val sql = s"select * from ${relTable.bindingTable} $whereSql"

    //    execute(sql).map { rs =>
    //      val endNode = direction match {
    //        case SemanticDirection.OUTGOING => nodeAt(LynxIntegerID(rs.getLong(relTable.sourceCol.columnName)), relTable.targetTableName)
    //        case SemanticDirection.INCOMING => nodeAt(LynxIntegerID(rs.getLong(relTable.targetCol.columnName)), relTable.targetTableName)
    //        case SemanticDirection.BOTH => nodeAt(LynxIntegerID(rs.getLong(relTable.targetCol.columnName)), relTable.targetTableName)
    //      }
    //      PathTriple(node, mapRel(rs, relTable.bindingTable, schema), endNode.get)
    //    }

    // select in
    val (ids, endTable) = direction match {
      case SemanticDirection.OUTGOING => (execute(sql).map { rs => LynxIntegerID(rs.getLong(relTable.sourceCol.columnName)) }, relTable.sourceTableName)
      case SemanticDirection.INCOMING => (execute(sql).map { rs => LynxIntegerID(rs.getLong(relTable.targetCol.columnName)) }, relTable.targetTableName)
      case SemanticDirection.BOTH => (execute(sql).map { rs => LynxIntegerID(rs.getLong(relTable.targetCol.columnName)) }, relTable.targetTableName)
    }
    nodesAt(ids, endTable).map(en => PathTriple(node,
      LynxJDBCRelationship(
        LynxIntegerID(nodeId.toLynxInteger.value),
        LynxIntegerID(nodeId.toLynxInteger.value),
        LynxIntegerID(en.id.toLynxInteger.value),
        Some(LynxRelationshipType(rel_Type)),
        schema.getTableByName(relTable.bindingTable).properties.map(prop => (LynxPropertyKey(prop.columnName), LynxValue(prop.dataType))).toMap
      ), en))

  }


  //  private def expandToIds(node: LynxNode, filter: RelationshipFilter, direction: SemanticDirection): Seq[LynxId] = {
  //    val nodeId = node.id
  //    val nodeLabel = node.labels.head.value
  //    val rel_Type = filter.types.head.value
  //    val rel_Condition = filter.properties.map {
  //      case (k, v) => s"${k.value} = ${v.value}"
  //    }
  //    val relTable = schema.getReltionshipByType(rel_Type)
  //    val whereSql = (direction match {
  //      case SemanticDirection.OUTGOING => s"where ${relTable.bindingTable}.${relTable.targetCol.columnName} = ${nodeId.toLynxInteger.value}"
  //      case SemanticDirection.INCOMING => s"where ${relTable.bindingTable}.${relTable.sourceCol.columnName} = ${nodeId.toLynxInteger.value}"
  //      case SemanticDirection.BOTH => s"where ${relTable.bindingTable}.${relTable.targetCol.columnName} = ${nodeId.toLynxInteger.value} or ${relTable.bindingTable}.${relTable.sourceCol.columnName} = ${nodeId.toLynxInteger.value}"
  //    }) + (if (rel_Condition.nonEmpty) " and " + rel_Condition.mkString(" and ") else "")
  //    val sql = s"select * from ${relTable.bindingTable} $whereSql"
  //
  //    // 执行 SQL，提取目标节点 ID
  //    direction match {
  //      case SemanticDirection.OUTGOING =>
  //        execute(sql).map(rs => LynxIntegerID(rs.getLong(relTable.sourceCol.columnName))).toSeq
  //      case SemanticDirection.INCOMING =>
  //        execute(sql).map(rs => LynxIntegerID(rs.getLong(relTable.targetCol.columnName))).toSeq
  //      case SemanticDirection.BOTH =>
  //        execute(sql).flatMap { rs =>
  //          Seq(
  //            LynxIntegerID(rs.getLong(relTable.sourceCol.columnName)),
  //            LynxIntegerID(rs.getLong(relTable.targetCol.columnName))
  //          )
  //        }.toSeq
  //    }
  //  }

  private def expandToIds(node: LynxNode, filter: RelationshipFilter, direction: SemanticDirection): Seq[LynxId] = {
    val nodeId = node.id
    val nodeLabel = node.labels.head.value
    val rel_Type = filter.types.head.value
    val rel_Condition = filter.properties.map {
      case (k, v) => s"${k.value} = ${v.value}"
    }
    val relTable = schema.getReltionshipByType(rel_Type)
    val whereSql = (direction match {
      case SemanticDirection.OUTGOING => s"where ${relTable.bindingTable}.${relTable.targetCol.columnName} = ${nodeId.toLynxInteger.value}"
      case SemanticDirection.INCOMING => s"where ${relTable.bindingTable}.${relTable.sourceCol.columnName} = ${nodeId.toLynxInteger.value}"
      case SemanticDirection.BOTH => s"where ${relTable.bindingTable}.${relTable.targetCol.columnName} = ${nodeId.toLynxInteger.value} or ${relTable.bindingTable}.${relTable.sourceCol.columnName} = ${nodeId.toLynxInteger.value}"
    }) + (if (rel_Condition.nonEmpty) " and " + rel_Condition.mkString(" and ") else "")
    val sql = s"select * from ${relTable.bindingTable} $whereSql"

    direction match {
      case SemanticDirection.OUTGOING =>
        execute(sql).map(rs => LynxIntegerID(rs.getLong(relTable.sourceCol.columnName))).toSeq
      case SemanticDirection.INCOMING =>
        execute(sql).map(rs => LynxIntegerID(rs.getLong(relTable.targetCol.columnName))).toSeq
      case SemanticDirection.BOTH =>
        execute(sql).flatMap { rs =>
          val sourceId = LynxIntegerID(rs.getLong(relTable.sourceCol.columnName))
          val targetId = LynxIntegerID(rs.getLong(relTable.targetCol.columnName))
          Seq(sourceId, targetId).filter(_ != LynxIntegerID(nodeId.toLynxInteger.value))
        }.toSeq
    }
  }

  private def expandToIds(nodeId: LynxId, nodeTable: String, filter: RelationshipFilter, direction: SemanticDirection): Seq[LynxId] = {
    val rel_Type = filter.types.head.value

    val rel_Condition = filter.properties.map {
      case (k, v) => s"${k.value} = ${v.value}"
    }
    val relTable = schema.getReltionshipByType(rel_Type)
    if (relTable.targetTableName != nodeTable && relTable.sourceTableName != nodeTable) return mutable.Seq.empty
    val whereSql = (direction match {
      case SemanticDirection.OUTGOING => s"where ${relTable.bindingTable}.${relTable.targetCol.columnName} = ${nodeId.toLynxInteger.value}"
      case SemanticDirection.INCOMING => s"where ${relTable.bindingTable}.${relTable.sourceCol.columnName} = ${nodeId.toLynxInteger.value}"
      case SemanticDirection.BOTH => s"where ${relTable.bindingTable}.${relTable.sourceCol.columnName} = ${nodeId.toLynxInteger.value} "
      //      case SemanticDirection.BOTH => s"where ${relTable.bindingTable}.${relTable.sourceCol.columnName} = ${nodeId.toLynxInteger.value} or ${relTable.bindingTable}.${relTable.sourceCol.columnName} = ${nodeId.toLynxInteger.value}"
    }) + (if (rel_Condition.nonEmpty) " and " + rel_Condition.mkString(" and ") else "")
    val sql = s"select ${relTable.sourceCol.columnName},${relTable.targetCol.columnName} from ${relTable.bindingTable} $whereSql"

    direction match {
      case SemanticDirection.OUTGOING =>
        execute(sql).map(rs => LynxIntegerID(rs.getLong(relTable.sourceCol.columnName))).toSeq
      case SemanticDirection.INCOMING =>
        execute(sql).map(rs => LynxIntegerID(rs.getLong(relTable.targetCol.columnName))).toSeq
      case SemanticDirection.BOTH =>
        execute(sql).flatMap { rs =>
          val sourceId = LynxIntegerID(rs.getLong(relTable.sourceCol.columnName))
          val targetId = LynxIntegerID(rs.getLong(relTable.targetCol.columnName))
          Seq(sourceId, targetId).filter(_ != LynxIntegerID(nodeId.toLynxInteger.value))
        }.toSeq
    }
  }

  private def expandToIdsBatch(
                                nodeIds: Iterator[LynxId],
                                nodeTable: String,
                                filter: RelationshipFilter,
                                direction: SemanticDirection,
                                batchSize: Int = 100
                              ): Iterator[LynxId] = {
    //TODO
    val rel_Type = filter.types.head.value
    val rel_Condition = filter.properties.map {
      case (k, v) => s"${k.value} = ${v.value}"
    }

    val relTable = schema.getReltionshipByType(rel_Type)
    if (relTable.targetTableName != nodeTable && relTable.sourceTableName != nodeTable) return Iterator.empty

    val whereSql = (direction match {
      case SemanticDirection.OUTGOING => s"where ${relTable.bindingTable}.${relTable.targetCol.columnName} in (?)"
      case SemanticDirection.INCOMING => s"where ${relTable.bindingTable}.${relTable.sourceCol.columnName} in (?)"
      case SemanticDirection.BOTH => s"where ${relTable.bindingTable}.${relTable.sourceCol.columnName} in (?) "
    }) + (if (rel_Condition.nonEmpty) " and " + rel_Condition.mkString(" and ") else "")

    nodeIds.grouped(batchSize).flatMap { batch =>
      val ids = batch.map(_.toLynxInteger.value).mkString(",")
      val sql = s"select ${relTable.sourceCol.columnName}, ${relTable.targetCol.columnName} from ${relTable.bindingTable} $whereSql"

      direction match {
        case SemanticDirection.OUTGOING =>
          execute(sql.replace("?", ids)).map(rs => LynxIntegerID(rs.getLong(relTable.sourceCol.columnName))).toSeq
        case SemanticDirection.INCOMING =>
          execute(sql.replace("?", ids)).map(rs => LynxIntegerID(rs.getLong(relTable.targetCol.columnName))).toSeq
        case SemanticDirection.BOTH =>
          execute(sql.replace("?", ids)).map(rs => LynxIntegerID(rs.getLong(relTable.targetCol.columnName))).toSeq
      }
    }
  }


  private def getNode(id: LynxId, tableName: String): Option[LynxNode] = {
    val nodeId = schema.getTableByName(tableName).priKeyCol.columnName
    singleTableSelect(tableName, List((nodeId, id.toLynxInteger.v)))
      .map(rs => Mapper.mapNode(rs, tableName, schema)).toSeq.headOption
  }

  private def getRel(sourceNode: LynxNode, targetNode: LynxNode, gRel: GraphRelationship): Option[LynxRelationship] = {
    val sql = s"select * from ${gRel.bindingTable} where ${gRel.sourceCol.columnName} = ${sourceNode.id} and  ${gRel.targetCol.columnName} = ${targetNode.id}"
    executeQuery(sql).map(rs => mapRel(rs, gRel.bindingTable, schema)).toSeq.headOption
  }

  private def nodesAt(ids: Iterator[LynxId], tableName: String): Iterator[LynxNode] = {
    val table = schema.getTableByName(tableName)

    table match {
      case RDBTable.empty =>
        Iterator.empty
      case t: RDBTable =>
        val idCondition = ids.map(id => s"id = ${id.value}").mkString(" or ")
        if (idCondition == "") return Iterator.empty
        val sql = s"select * from ${t.tableName} where $idCondition"
        executeQuery(sql).map(rs => Mapper.mapNode(rs, tableName, schema))
    }
  }

  private def nodesAtBatch(ids: Iterator[LynxId], tableName: String, batchSize: Int = 100): Iterator[LynxNode] = {
    val table = schema.getTableByName(tableName)

    table match {
      case RDBTable.empty =>
        Iterator.empty
      case t: RDBTable =>
        val batchedIds = ids.grouped(batchSize)
        new Iterator[LynxNode] {
          private var currentBatch: Iterator[LynxId] = _
          private var currentResults: Iterator[LynxNode] = Iterator.empty

          def loadNextBatch(): Unit = {
            if (batchedIds.hasNext) {
              currentBatch = batchedIds.next().toIterator
              val idCondition = currentBatch.map(id => s"${id.value}").mkString(",")
              if (idCondition.nonEmpty) {
                val sql = s"select * from ${t.tableName} where id in ($idCondition)"
                currentResults = execute(sql).map(rs => Mapper.mapNode(rs, tableName, schema))
              } else {
                currentResults = Iterator.empty
              }
            } else {
              currentResults = Iterator.empty
            }
          }

          def hasNext: Boolean = {
            if (currentResults.isEmpty) {
              loadNextBatch()
            }
            currentResults.hasNext
          }

          def next(): LynxNode = {
            if (hasNext) {
              currentResults.next()
            } else {
              throw new NoSuchElementException("No more nodes available.")
            }
          }
        }
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

  //    override def expandNonStop(start: LynxNode, relationshipFilter: RelationshipFilter, direction: SemanticDirection, steps: Int): Iterator[LynxPath] = {
  //      if (steps < 0) return Iterator(LynxPath.EMPTY)
  //      if (steps == 0) return Iterator(LynxPath.startPoint(start))
  //      //    expand(start.id, relationshipFilter, direction).flatMap{ triple =>
  //      //      expandNonStop(triple.endNode, relationshipFilter, direction, steps - 1).map{_.connectLeft(triple.toLynxPath)}
  //      //    }
  //      // TODO check cycle
  //      expand(start, relationshipFilter, direction)
  //        .flatMap { triple =>
  //          expandNonStop(triple.endNode, relationshipFilter, direction, steps - 1)
  //            .filterNot(_.nodeIds.contains(triple.startNode.id))
  //            .map {
  //              _.connectLeft(triple.toLynxPath)
  //            }
  //        }
  //    }

  //  private def bfsWithFullPaths1(
  //                                 startNode: LynxNode,
  //                                 relationshipFilter: RelationshipFilter,
  //                                 direction: SemanticDirection,
  //                                 maxSteps: Int
  //                               ): Iterator[Seq[Seq[LynxId]]] = {
  //
  //    val visited = mutable.Set[LynxId]()
  //    val initialFrontier = mutable.HashMap[LynxId, Seq[Seq[LynxId]]](startNode.id -> Seq(Seq(startNode.id)))
  //
  //    var cacheTotal = 0L
  //    var cacheMisses = 0L
  //
  //    def bfs(): Iterator[Seq[Seq[LynxId]]] = {
  //      val result = mutable.ListBuffer[Seq[Seq[LynxId]]]()
  //
  //      var currentFrontier = initialFrontier
  //      var step = 0
  //
  //      while (currentFrontier.nonEmpty && step < maxSteps) {
  //        val currentLayerPaths = currentFrontier.values.flatten.toSeq
  //        result += currentLayerPaths
  //
  //        val nextFrontier = mutable.HashMap[LynxId, Seq[Seq[LynxId]]]()
  //
  //        currentFrontier.foreach { case (currentNodeId, paths) =>
  //          if (!visited.contains(currentNodeId)) {
  //            visited.add(currentNodeId)
  //
  //            val neighborIds = GolbalCache.getOrElseUpdate(
  //              {
  //                cacheTotal += 1
  //                currentNodeId
  //              }, {
  //                cacheMisses += 1
  //                val nodeTable = schema.getNodeByNodeLabel(startNode.labels.head.value).tableName
  //                expandToIds(currentNodeId, nodeTable, relationshipFilter, direction)
  //              })
  //
  //            neighborIds.foreach { neighborId =>
  //              val newPaths = paths.map(_ :+ neighborId)
  //              nextFrontier.get(neighborId) match {
  //                case Some(existingPaths) => nextFrontier.update(neighborId, existingPaths ++ newPaths)
  //                case None => nextFrontier.update(neighborId, newPaths)
  //              }
  //            }
  //          }
  //        }
  //
  //        currentFrontier = nextFrontier
  //        step += 1
  //      }
  //
  //      val hitRate = 100L * (cacheTotal - cacheMisses) / cacheTotal
  //      logger.info(f"Final Cache Hit Rate: $hitRate%% ($cacheTotal total, ${cacheTotal - cacheMisses} hits)")
  //
  //      result.iterator
  //    }
  //
  //    bfs()
  //  }

  //  private def bfsWithFullPaths2(
  //                                 startNode: LynxNode,
  //                                 relationshipFilter: RelationshipFilter,
  //                                 direction: SemanticDirection,
  //                                 maxSteps: Int
  //                               ): Iterator[Seq[Seq[LynxId]]] = {
  //
  //    def bfsRecursive(
  //                      currentFrontier: mutable.HashMap[LynxId, Seq[Seq[LynxId]]],
  //                      step: Int
  //                    ): Iterator[Seq[Seq[LynxId]]] = {
  //      if (currentFrontier.isEmpty || step >= maxSteps) {
  //        Iterator.empty
  //      } else {
  //        var cacheTotal = 0
  //        var cacheMisses = 0
  //        val currentLayerPaths = currentFrontier.values.flatten.toSeq
  //        val nextFrontier = mutable.HashMap[LynxId, Seq[Seq[LynxId]]]()
  //        currentFrontier.foreach { case (currentNodeId, paths) =>
  //          val neighborIds = GolbalCache.getOrElseUpdate(
  //            {
  //              cacheTotal += 1
  //              currentNodeId
  //            }, {
  //              cacheMisses += 1
  //              //              logger.info(s"Cache miss for node: $currentNodeId")
  //              val nodeTable = schema.getNodeByNodeLabel(startNode.labels.head.value).tableName
  //              expandToIds(currentNodeId, nodeTable, relationshipFilter, direction)
  //            })
  //          neighborIds.foreach { neighborId =>
  //            val newPaths = paths.map(_ :+ neighborId)
  //            nextFrontier.get(neighborId) match {
  //              case Some(existingPaths) => nextFrontier.update(neighborId, existingPaths ++ newPaths)
  //              case None => nextFrontier.update(neighborId, newPaths)
  //            }
  //          }
  //        }
  //        val rate: Long = 100 * (cacheTotal - cacheMisses) / cacheTotal
  //        logger.info(s"Cache hit rate: $rate% hit:${cacheTotal - cacheMisses} t:$cacheTotal")
  //        Iterator.single(currentLayerPaths) ++ bfsRecursive(nextFrontier, step + 1)
  //      }
  //    }
  //
  //    val startNodeId = startNode.id
  //    val initialFrontier = mutable.HashMap(startNodeId -> Seq(Seq(startNodeId)))
  //    bfsRecursive(initialFrontier, 0)
  //  }

  //  private def bfsWithFullPaths3(
  //                                 startNode: LynxNode,
  //                                 relationshipFilter: RelationshipFilter,
  //                                 direction: SemanticDirection,
  //                                 maxSteps: Int
  //                               ): Iterator[Seq[Seq[LynxId]]] = {
  //
  //    val visited = mutable.Set[LynxId]()
  //    val initialFrontier = mutable.HashMap[LynxId, Seq[Seq[LynxId]]](startNode.id -> Seq(Seq(startNode.id)))
  //    var cacheTotal = 0L
  //    var cacheMisses = 0L
  //
  //    def bfs(): Iterator[Seq[Seq[LynxId]]] = {
  //      val result = mutable.ListBuffer[Seq[Seq[LynxId]]]()
  //
  //      var currentFrontier = initialFrontier
  //      var step = 0
  //
  //      while (currentFrontier.nonEmpty && step < maxSteps) {
  //        val currentLayerPaths = currentFrontier.values.flatten.toSeq
  //        result += currentLayerPaths
  //
  //        val nextFrontier = mutable.HashMap[LynxId, Seq[Seq[LynxId]]]()
  //
  //        currentFrontier.foreach { case (currentNodeId, paths) =>
  //          if (!visited.contains(currentNodeId)) {
  //            visited.add(currentNodeId)
  //
  //            val neighborIds = GolbalCache.getOrElseUpdate(
  //              {
  //                cacheTotal += 1
  //                currentNodeId
  //              }, {
  //                cacheMisses += 1
  //                val nodeTable = schema.getNodeByNodeLabel(startNode.labels.head.value).tableName
  //                expandToIds(currentNodeId, nodeTable, relationshipFilter, direction)
  //              })
  //            neighborIds.foreach { neighborId =>
  //              val newPaths = paths.map(_ :+ neighborId)
  //              val existingPaths = nextFrontier.getOrElse(neighborId, Seq())
  //              nextFrontier.update(neighborId, existingPaths ++ newPaths)
  //            }
  //          }
  //        }
  //        currentFrontier = nextFrontier
  //        step += 1
  //      }
  //      val hitRate = 100L * (cacheTotal - cacheMisses) / cacheTotal
  //      logger.info(f"Final Cache Hit Rate: $hitRate%% ($cacheTotal total, ${cacheTotal - cacheMisses} hits)")
  //      result.iterator
  //    }
  //
  //    bfs()
  //  }


  private def bfsWithFullPaths4(
                                 startNode: LynxNode,
                                 relationshipFilter: RelationshipFilter,
                                 direction: SemanticDirection,
                                 maxSteps: Int
                               ): Iterator[Seq[Seq[LynxId]]] = {

    val visited = mutable.Set[LynxId]()
    val initialFrontier = mutable.HashMap[LynxId, Seq[Seq[LynxId]]](startNode.id -> Seq(Seq(startNode.id)))
    var cacheTotal = 0L
    var cacheMisses = 0L

    def bfs(): Iterator[Seq[Seq[LynxId]]] = {
      val result = mutable.ListBuffer[Seq[Seq[LynxId]]]()

      var currentFrontier = initialFrontier
      var step = 0

      // 普通步骤：循环并扩展前沿
      while (currentFrontier.nonEmpty && step < maxSteps) {
        val currentLayerPaths = currentFrontier.values.flatten.toSeq
        result += currentLayerPaths

        val nextFrontier = mutable.HashMap[LynxId, Seq[Seq[LynxId]]]()

        currentFrontier.foreach { case (currentNodeId, paths) =>
          if (!visited.contains(currentNodeId)) {
            visited.add(currentNodeId)

            val neighborIds = GolbalCache.getOrElseUpdate(
              {
                cacheTotal += 1
                currentNodeId
              }, {
                cacheMisses += 1
                val nodeTable = schema.getNodeByNodeLabel(startNode.labels.head.value).tableName
                expandToIds(currentNodeId, nodeTable, relationshipFilter, direction)
              })
            neighborIds.foreach { neighborId =>
              val newPaths = paths.map(_ :+ neighborId)
              val existingPaths = nextFrontier.getOrElse(neighborId, Seq())
              nextFrontier.update(neighborId, existingPaths ++ newPaths)
            }
          }
        }
        currentFrontier = nextFrontier
        step += 1
      }

      if (currentFrontier.nonEmpty) {
        val currentLayerPaths = currentFrontier.values.flatten.toSeq
        result += currentLayerPaths
      }

      val hitRate = 100L * (cacheTotal - cacheMisses) / cacheTotal
      logger.info(f"Final Cache Hit Rate: $hitRate%% ($cacheTotal total, ${cacheTotal - cacheMisses} hits)")

      result.iterator
    }

    bfs()
  }

  //  private def bfsLastFrontier1(
  //                                startNode: LynxNode,
  //                                relationshipFilter: RelationshipFilter,
  //                                direction: SemanticDirection,
  //                                maxSteps: Int
  //                              ): Seq[LynxId] = {
  //
  //    val visited = mutable.Set[LynxId]()
  //    val initialFrontier = mutable.Set[LynxId](startNode.id)
  //    var cacheTotal = 0L
  //    var cacheMisses = 0L
  //    val nodeTable = schema.getNodeByNodeLabel(startNode.labels.head.value).tableName
  //
  //    def bfs(): Seq[LynxId] = {
  //      var currentFrontier = initialFrontier
  //      var step = 0
  //      while (currentFrontier.nonEmpty && step < maxSteps) {
  //        val nextFrontier = mutable.Set[LynxId]()
  //        currentFrontier.foreach { currentNodeId =>
  //          if (visited.add(currentNodeId)) {
  //            val neighborIds = GolbalCache.getOrElseUpdate(
  //              {
  //                cacheTotal += 1
  //                currentNodeId
  //              }, {
  //                cacheMisses += 1
  //                expandToIds(currentNodeId, nodeTable, relationshipFilter, direction)
  //              }
  //            )
  //            neighborIds.foreach(nextFrontier.add)
  //          }
  //        }
  //        currentFrontier = nextFrontier
  //        step += 1
  //      }
  //      val hitRate = if (cacheTotal > 0) 100L * (cacheTotal - cacheMisses) / cacheTotal else 0L
  //      logger.info(f"Final Cache Hit Rate: $hitRate%% ($cacheTotal total, ${cacheTotal - cacheMisses} hits)")
  //      currentFrontier.toSeq
  //    }
  //
  //    bfs()
  //  }

  private def bfsLastFrontier(
                               startNode: LynxNode,
                               relationshipFilter: RelationshipFilter,
                               direction: SemanticDirection,
                               maxSteps: Int
                             ): Seq[LynxId] = {

    val initialFrontier = mutable.Set[LynxId](startNode.id)
    val nodeTable = schema.getNodeByNodeLabel(startNode.labels.head.value).tableName

    def bfs(): Seq[LynxId] = {
      var currentFrontier = initialFrontier
      var step = 0
      while (currentFrontier.nonEmpty && step < maxSteps) {
        val nextFrontier = mutable.Set[LynxId]()
        currentFrontier.foreach { currentNodeId =>
          val neighborIds = GolbalCache.getOrElseUpdate(
            currentNodeId,
            expandToIds(currentNodeId, nodeTable, relationshipFilter, direction)
          )
          nextFrontier ++= neighborIds
        }
        currentFrontier = nextFrontier
        step += 1
      }
      currentFrontier.toSeq
    }

    bfs()
  }

  private def bfsLastFrontierBatch(
                                    startNode: LynxNode,
                                    relationshipFilter: RelationshipFilter,
                                    direction: SemanticDirection,
                                    maxSteps: Int,
                                    batchSize: Int = 1000
                                  ): Seq[LynxId] = {

    val initialFrontier = mutable.Set[LynxId](startNode.id)
    val nodeTable = schema.getNodeByNodeLabel(startNode.labels.head.value).tableName

    def batchExpandToIds(nodeIds: Iterator[LynxId]): Iterator[LynxId] = {
      nodeIds.grouped(batchSize).flatMap { batch =>
        val neighborIds = expandToIdsBatch(batch.iterator, nodeTable, relationshipFilter, direction, batchSize)
        neighborIds
      }
    }

    def bfs(): Seq[LynxId] = {
      var currentFrontier = initialFrontier
      var step = 0
      while (currentFrontier.nonEmpty && step < maxSteps) {
        val nextFrontier = mutable.Set[LynxId]()
        batchExpandToIds(currentFrontier.iterator).foreach { neighborId =>
          nextFrontier += neighborId
        }
        currentFrontier = nextFrontier
        step += 1
      }
      currentFrontier.toSeq
    }

    bfs()
  }

  private def bfsLastFrontierBatchWithCache(
                                             startNode: LynxNode,
                                             relationshipFilter: RelationshipFilter,
                                             direction: SemanticDirection,
                                             maxSteps: Int,
                                             batchSize: Int = 1000
                                           ): Seq[LynxId] = {

    val initialFrontier = mutable.Set[LynxId](startNode.id)
    val nodeTable = schema.getNodeByNodeLabel(startNode.labels.head.value).tableName
    var hit = 0

    def batchExpandToIds(nodeIds: Iterator[LynxId]): Iterator[LynxId] = {
      nodeIds.grouped(batchSize).flatMap { batch =>
        val nodeIdsToFetch = batch.filterNot(nodeId => GolbalCache.contains(nodeId))
        val missingNeighbors = expandToIdsBatch(nodeIdsToFetch.iterator, nodeTable, relationshipFilter, direction, batchSize)
        val cachedNeighbors = batch.filter(GolbalCache.contains).flatMap { nodeId =>
          GolbalCache.getOrElse(nodeId, Seq.empty)
        }
        missingNeighbors ++ cachedNeighbors
      }
    }


    def bfs(): Seq[LynxId] = {
      var currentFrontier = initialFrontier
      var step = 0
      while (currentFrontier.nonEmpty && step < maxSteps) {
        val nextFrontier = mutable.Set[LynxId]()
        batchExpandToIds(currentFrontier.iterator).foreach { neighborId =>
          nextFrontier += neighborId
        }
        currentFrontier = nextFrontier
        step += 1
      }
      currentFrontier.toSeq
    }

    bfs()
  }


  override def expandNonStop(
                              start: LynxNode,
                              relationshipFilter: RelationshipFilter,
                              direction: SemanticDirection,
                              steps: Int
                            ): Iterator[LynxPath] = {
    val switchMaker = 1
    if (steps <= 0) return Iterator(LynxPath.startPoint(start))
    steps match {
      case 1 => expand(start, relationshipFilter, direction)
        .flatMap { triple =>
          expandNonStop(triple.endNode, relationshipFilter, direction, steps - 1)
            .filterNot(_.nodeIds.contains(triple.startNode.id))
            .map {
              _.connectLeft(triple.toLynxPath)
            }
        }
      case _ => switchMaker match {
        case 1 =>
          val nodeLabel = start.labels.head.value
          val nodeTable = schema.getNodeByNodeLabel(nodeLabel).tableName
          val relType = relationshipFilter.types.head.value
          val relTable = schema.getReltionshipByType(relType)
          val bfsIterator = bfsWithFullPaths4(start, relationshipFilter, direction, steps)

          val bfsSeq = bfsIterator.toSeq
          logger.info(f"${bfsSeq.last.length}")
          bfsSeq.last.iterator.map { ids =>
            ids.zipWithIndex.flatMap { case (nodeId, index) =>
              val currentNode = getNode(nodeId, nodeTable).get
              if (index < ids.length - 1) {
                val nextNodeId = ids(index + 1)
                val relationship = getRel(currentNode, getNode(nextNodeId, nodeTable).get, relTable).get
                Seq(currentNode, relationship)
              } else {
                Seq(currentNode)
              }
            }
          } map { path => LynxPath(path) }

        case 2 =>
          val nodeLabel = start.labels.head.value
          val nodeTable = schema.getNodeByNodeLabel(nodeLabel).tableName
          val bfsIterator = bfsLastFrontierBatch(start, relationshipFilter, direction, steps)
          logger.info(f"${bfsIterator.length}")
          //          bfsIterator.iterator.map { endId =>
          //            val multiRelationshipPath = LynxJDBCRelationship(
          //              LynxIntegerID(start.id.toLynxInteger.v),
          //              LynxIntegerID(start.id.toLynxInteger.v),
          //              LynxIntegerID(endId.toLynxInteger.v),
          //              Some(relationshipFilter.types.head),
          //              Map.empty
          //            )
          //            val endNode = getNode(endId, nodeTable).get
          //            PathTriple(start, multiRelationshipPath, endNode).toLynxPath
          //          }
          nodesAtBatch(bfsIterator.iterator, nodeTable).map { endNode =>
            LynxPath(Seq(start, LynxJDBCRelationship(
              LynxIntegerID(start.id.toLynxInteger.v),
              LynxIntegerID(start.id.toLynxInteger.v),
              LynxIntegerID(endNode.id.toLynxInteger.v),
              Some(relationshipFilter.types.head),
              Map.empty), endNode))
          }
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
