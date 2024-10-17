package schema

import play.api.libs.json.{Json, Reads, Writes}
import schema.element._

/**
 * Used to record the structure of tables in the database, including primary-foreign key relationships and other properties
 *
 * @param tableName Name of the table
 * @param priKeyCol Primary key of the table
 * @param forKeyCol Foreign keys of the table
 * @param tableType node/ relationship/ both
 * @param properties
 */

case class RDBTable(tableName: String,
                    priKeyCol: PKProperty,
                    forKeyCol: Array[FKProperty],
                    tableType: String,
                    properties: Array[BaseProperty]) {

}

object RDBTable {
  val empty: RDBTable = RDBTable(
    tableName = null,
    priKeyCol = null,
    forKeyCol = null,
    tableType = null,
    properties = null,
  )
  //  val empty: GraphTable = GraphTable(null, PKProperty.empty, null, null, null)
  implicit val tableWrites: Writes[RDBTable] = Json.writes[RDBTable]
  implicit val tableReads: Reads[RDBTable] = Json.reads[RDBTable]
}