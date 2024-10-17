package schema.element

import play.api.libs.json.{Json, Reads, Writes}

case class PKProperty(columnName: String, dataType: String, nodeLabel: String) extends Property

object PKProperty {
  val empty = PKProperty("", "", "")
  implicit val pkPropertyWrites: Writes[PKProperty] = Json.writes[PKProperty]
  implicit val pkPropertyReads: Reads[PKProperty] = Json.reads[PKProperty]
}
