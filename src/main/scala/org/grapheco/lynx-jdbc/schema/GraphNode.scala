package schema

import play.api.libs.json.{Json, Reads, Writes}

/**
 *
 * @param nodeName     node name
 * @param nodeLabel    node label
 * @param bindingTable which table the node binding to
 */
case class GraphNode(nodeName: String,
                     nodeLabel: String,
                     bindingTable: String,
                    )

object GraphNode {
  val empty = GraphNode(null, null, null)
  implicit val tripleWrites: Writes[GraphNode] = Json.writes[GraphNode]
  implicit val tripleReads: Reads[GraphNode] = Json.reads[GraphNode]
}
