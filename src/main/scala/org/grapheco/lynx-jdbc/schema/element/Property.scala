package schema.element

import play.api.libs.json.{JsValue, Json, Reads, Writes}

trait Property {
  def columnName: String

  def dataType: String
}

object Property {

  val empty: Property = new Property {
    val columnName: String = null
    val dataType: String = null
  }

  implicit val propertyWrites: Writes[Property] = new Writes[Property] {
    def writes(property: Property): JsValue = property match {
      case pk: PKProperty => Json.toJson(pk)(PKProperty.pkPropertyWrites)
      case fk: FKProperty => Json.toJson(fk)(FKProperty.fkPropertyWrites)
      case _ => Json.obj("columnName" -> property.columnName, "dataType" -> property.dataType)
    }
  }

  implicit val propertyReads: Reads[Property] = Property.propertyReads
}

