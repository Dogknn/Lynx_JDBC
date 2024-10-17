package schema

import org.opencypher.v9_0.expressions.SemanticDirection
import play.api.libs.json.{Json, Reads, Writes}
import schema.element.Property

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
