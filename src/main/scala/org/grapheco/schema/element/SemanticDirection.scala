//package schema
//
//import org.opencypher.v9_0.expressions.SemanticDirection
//import play.api.libs.json.{Json, Reads, Writes, JsString, JsError}
//
//object SemanticDirection extends Enumeration {
//  type SemanticDirection = Value
//  val INCOMING, OUTGOING, BOTH = Value
//}
//
//object GraphRelationship {
//
//  implicit val semanticDirectionWrites: Writes[SemanticDirection.Value] = Writes {
//    case SemanticDirection.INCOMING => JsString("INCOMING")
//    case SemanticDirection.OUTGOING => JsString("OUTGOING")
//    case SemanticDirection.BOTH     => JsString("BOTH")
//  }
//
//  implicit val semanticDirectionReads: Reads[SemanticDirection.Value] = Reads {
//    case JsString("INCOMING") => JsSuccess(SemanticDirection.INCOMING)
//    case JsString("OUTGOING") => JsSuccess(SemanticDirection.OUTGOING)
//    case JsString("BOTH")     => JsSuccess(SemanticDirection.BOTH)
//    case _                    => JsError("Unknown SemanticDirection")
//  }
//
//  implicit val tripleWrites: Writes[GraphRelationship] = Json.writes[GraphRelationship]
//  implicit val tripleReads: Reads[GraphRelationship] = Json.reads[GraphRelationship]
//}
