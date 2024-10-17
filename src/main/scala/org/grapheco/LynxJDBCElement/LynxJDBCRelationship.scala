package LynxJDBCElement

import org.grapheco.lynx.types.LynxValue
import org.grapheco.lynx.types.structural.{LynxPropertyKey, LynxRelationship, LynxRelationshipType}

case class LynxJDBCRelationship(id: LynxIntegerID,
                                startNodeId: LynxIntegerID,
                                endNodeId: LynxIntegerID,
                                relationType: Option[LynxRelationshipType],
                                props: Map[LynxPropertyKey, LynxValue]) extends LynxRelationship {
  override def property(propertyKey: LynxPropertyKey): Option[LynxValue] = props.get(propertyKey)

  override def keys: Seq[LynxPropertyKey] = props.keys.toSeq
}