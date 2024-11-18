package LynxJDBCElement

import org.grapheco.lynx.types.property.LynxInteger
import org.grapheco.lynx.types.structural.LynxId

case class LynxIntegerID(value: Long) extends LynxId {
  override def toLynxInteger: LynxInteger = LynxInteger(value)
}
