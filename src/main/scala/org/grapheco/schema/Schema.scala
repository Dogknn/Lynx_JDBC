package schema

import schema.element._

case class Schema(tables: Seq[RDBTable], gNodes: Seq[GraphNode], gRelationship: Seq[GraphRelationship]) {

  def getTableByName(tableName: String): RDBTable = tables.find(_.tableName == tableName).getOrElse(RDBTable.empty)

  def getNodeByNodeLabel(nodeLabel: String): RDBTable = gNodes.find(_.nodeLabel == nodeLabel).getOrElse(GraphNode.empty) match {
    case GraphNode.empty => RDBTable.empty
    case v => tables.find(_.tableName == v.bindingTable).getOrElse(RDBTable.empty)
  }

  def getTableByRelationshipType(rType: String): RDBTable = gRelationship.find(_.relationshipType == rType).getOrElse(GraphRelationship.empty) match {
    case GraphNode.empty => RDBTable.empty
    case v => tables.find(_.tableName == v.bindingTable).getOrElse(RDBTable.empty)
  }

  def getReltionshipByType(rType: String): GraphRelationship = gRelationship.find(_.relationshipType == rType).getOrElse(GraphRelationship.empty)

  //private def checkProperty(property: Property, nodeLabel: String): Boolean = {
  //  property match {
  //    case v: FKProperty => getTableByName(v.referenceTableName) == nodeLabel
  //    case v: PKProperty => v.nodeLabel == nodeLabel
  //  }
  //}
}

object Schema {
  val empty = Schema(null, null, null)
}