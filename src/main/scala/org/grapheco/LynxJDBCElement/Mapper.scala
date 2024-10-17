package LynxJDBCElement

import org.grapheco.lynx.types.LynxValue
import org.grapheco.lynx.types.structural.{LynxNodeLabel, LynxPropertyKey, LynxRelationshipType}
import schema.element.{BaseProperty, PKProperty, Property}
import schema._

import java.sql.ResultSet
import java.time.LocalDate

object Mapper {

  private def mapId(row: ResultSet, property: PKProperty): LynxIntegerID = LynxIntegerID(row.getLong(property.columnName))

  private def mapRelId(row: ResultSet, property: PKProperty): LynxIntegerID = LynxIntegerID(row.getLong(property.columnName))

  private def mapStartId(row: ResultSet, property: Property): LynxIntegerID = LynxIntegerID(row.getLong(property.columnName))

  private def mapEndId(row: ResultSet, property: Property): LynxIntegerID = LynxIntegerID(row.getLong(property.columnName))

  //  private def findNode(row: ResultSet, property: Property):LynxJDBCNode =

  private def mapProps(row: ResultSet, mapper: Array[BaseProperty]): Map[LynxPropertyKey, LynxValue] = {
    mapper.map { case gp =>
      val i = row.findColumn(gp.columnName)
      LynxPropertyKey(gp.columnName) -> LynxValue(gp.dataType match {
        case "String" => row.getString(i)
        case "BIGINT" => row.getLong(i)
        case "INT" => row.getInt(i)
        case "Date" => LocalDate.ofEpochDay(row.getDate(i).getTime / 86400000)
        case _ => row.getString(i)
      })
    }.toMap
  }


  def mapNode(row: ResultSet, tableName: String, schema: Schema): LynxJDBCNode = {
    val _schema = schema.getTableByName(tableName)
    if (_schema == RDBTable.empty) throw new Exception("schema not find")
    LynxJDBCNode(mapId(row, _schema.priKeyCol), Seq(LynxNodeLabel(_schema.priKeyCol.nodeLabel)), mapProps(row, _schema.properties))
  }

  def mapRel(row: ResultSet, tableName: String, schema: Schema): LynxJDBCRelationship = {
    val _triple = schema.getReltionshipByType(tableName)
    val _graphTable = schema.getTableByName(tableName)
    if (_triple == GraphRelationship.empty) throw new Exception("schema not find")
    //TODO  getByFK
    //Startid representing rel id
    LynxJDBCRelationship(
      mapStartId(row, _triple.sourceCol),
      mapStartId(row, _triple.sourceCol),
      mapEndId(row, _triple.targetCol),
      Some(LynxRelationshipType(_triple.relationshipType)),
      _graphTable.properties.map(prop => (LynxPropertyKey(prop.columnName), LynxValue(prop.dataType))).toMap
    )
  }
}
