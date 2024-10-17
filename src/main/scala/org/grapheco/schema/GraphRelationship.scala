package schema

import org.opencypher.v9_0.expressions.SemanticDirection
import play.api.libs.json.{Json, Reads, Writes, JsString, JsError, JsSuccess}
import schema.element.Property

/** *
 *
 * @param relationshipType Type of relationship in relationshipTable, typically matching tableName.
 * @param sourceCol        First foreign key of the relationshipTable, representing the sourceNode.
 * @param direction        Direction of the relationship, categorized as outgoing, incoming, or bidirectional.
 * @param targetCol        Second foreign key of the relationshipTable, representing the targetNode.
 */

case class GraphRelationship(relationshipType: String,
                             sourceTableName: String,
                             sourceCol: Property,
                             direction: SemanticDirection,
                             targetTableName: String,
                             targetCol: Property,
                             bindingTable: String
                            )

object GraphRelationship {
  val empty = GraphRelationship(null, null, Property.empty, null, null, Property.empty, null)

  implicit val semanticDirectionWrites: Writes[SemanticDirection] = Writes {
    case SemanticDirection.OUTGOING => JsString("OUTGOING")
    case SemanticDirection.INCOMING => JsString("INCOMING")
    case SemanticDirection.BOTH => JsString("BOTH")
  }

  implicit val semanticDirectionReads: Reads[SemanticDirection] = Reads {
    case JsString("OUTGOING") => JsSuccess(SemanticDirection.OUTGOING)
    case JsString("INCOMING") => JsSuccess(SemanticDirection.INCOMING)
    case JsString("BOTH") => JsSuccess(SemanticDirection.BOTH)
    case _ => JsError("Unknown SemanticDirection")
  }

  implicit val tripleWrites: Writes[GraphRelationship] = Json.writes[GraphRelationship]
  implicit val tripleReads: Reads[GraphRelationship] = Json.reads[GraphRelationship]
}
