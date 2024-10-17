package schema.element

import play.api.libs.json.{Json, Reads, Writes}

case class BaseProperty(columnName: String, dataType: String) extends Property

object BaseProperty {
  val empty = BaseProperty("", "")
  implicit val genericPropertyWrites: Writes[BaseProperty] = Json.writes[BaseProperty]
  implicit val fkPropertyReads: Reads[BaseProperty] = Json.reads[BaseProperty]
}