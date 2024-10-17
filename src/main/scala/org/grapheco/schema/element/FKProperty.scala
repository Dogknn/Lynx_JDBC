package schema.element

import play.api.libs.json.{Json, Reads, Writes}

case class FKProperty(columnName: String, dataType: String, referenceTableName: String, referenceTableCol: String) extends Property

object FKProperty {
  val empty = FKProperty("", "", "", "")
  implicit val fkPropertyWrites: Writes[FKProperty] = Json.writes[FKProperty]
  implicit val fkPropertyReads: Reads[FKProperty] = Json.reads[FKProperty]
}