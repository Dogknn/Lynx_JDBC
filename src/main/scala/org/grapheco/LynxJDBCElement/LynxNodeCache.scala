//import com.github.benmanes.caffeine.cache.{Cache, Caffeine}
//import java.sql.{Connection, ResultSet}
//import scala.collection.mutable
//import scala.jdk.CollectionConverters._
//
//case class LynxNode(id: Int)
//case class LynxPath(nodes: List[LynxNode])
//
//class GraphBFS(databaseConnection: Connection) {
//
//  private val cache: Cache[Int, ResultSet] = Caffeine.newBuilder()
//    .maximumSize(1000)  // 最大缓存项数
//    .expireAfterWrite(10, java.util.concurrent.TimeUnit.MINUTES)  // 缓存过期时间
//    .build()
//
//  // 使用 BFS 和缓存进行节点搜索
//  def bfsWithCaching(startNode: LynxNode, targetNode: Option[LynxNode] = None): Option[LynxPath] = {
//    val queue = mutable.Queue[LynxPath](LynxPath(List(startNode)))
//    val visited = mutable.Set[Int](startNode.id)
//
//    while (queue.nonEmpty) {
//      val currentPath = queue.dequeue()
//      val currentNode = currentPath.nodes.last  // 当前路径的最后一个节点
//
//      // 如果找到目标节点则返回路径
//      if (targetNode.isDefined && currentNode == targetNode.get) {
//        return Some(currentPath)
//      }
//
//      // 执行查询，先检查缓存
//      val neighbors = getNeighborsWithCaching(currentNode.id)
//
//      // 处理查询到的邻居节点
//      neighbors.foreach { neighbor =>
//        if (!visited.contains(neighbor.id)) {
//          visited += neighbor.id
//          queue.enqueue(LynxPath(currentPath.nodes :+ neighbor))  // 将新路径加入队列
//        }
//      }
//    }
//    None  // 未找到目标节点则返回 None
//  }
//
//  // 使用缓存查询指定节点的邻居
//  private def getNeighborsWithCaching(nodeId: Int): List[LynxNode] = {
//    val cachedResult = cache.getIfPresent(nodeId)
//
//    if (cachedResult != null) {
//      resultSetToLynxNodes(cachedResult)  // 缓存命中则直接返回
//    } else {
//      // 若未命中缓存，则执行数据库查询
//      val resultSet = executeSelectQuery(nodeId)
//      cache.put(nodeId, resultSet)  // 将结果存入缓存
//      resultSetToLynxNodes(resultSet)
//    }
//  }
//
//  // 执行 SELECT 查询以获取节点的邻居
//  private def executeSelectQuery(nodeId: Int): ResultSet = {
//    val statement = databaseConnection.prepareStatement(
//      s"SELECT neighbor_id FROM relationships WHERE node_id = ?"
//    )
//    statement.setInt(1, nodeId)
//    statement.executeQuery()
//  }
//
//  // 将 ResultSet 转换为 LynxNode 列表
//  private def resultSetToLynxNodes(resultSet: ResultSet): List[LynxNode] = {
//    val nodes = mutable.ListBuffer[LynxNode]()
//    while (resultSet.next()) {
//      nodes += LynxNode(resultSet.getInt("neighbor_id"))
//    }
//    nodes.toList
//  }
//}
