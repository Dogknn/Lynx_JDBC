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

  // 确保你有 PKProperty 和 FKProperty 的具体实现
  implicit val propertyWrites: Writes[Property] = new Writes[Property] {
    def writes(property: Property): JsValue = property match {
      case pk: PKProperty => Json.toJson(pk)(PKProperty.pkPropertyWrites)
      case fk: FKProperty => Json.toJson(fk)(FKProperty.fkPropertyWrites)
      // 不再使用 generic: Property
      // 这里可以选择抛出异常或者使用一个默认的 JSON 表示
      case _ => Json.obj("columnName" -> property.columnName, "dataType" -> property.dataType)
    }
  }

  implicit val propertyReads: Reads[Property] = Property.propertyReads
}

