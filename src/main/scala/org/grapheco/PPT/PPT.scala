//package PPT
//
//import org.grapheco.lynx.LynxType
//import org.grapheco.lynx.dataframe.DataFrame
//import org.grapheco.lynx.physical.{PPTExpandPath, PPTNode, PhysicalPlannerContext}
//import org.grapheco.lynx.runner.{ExecutionContext, NodeFilter, RelationshipFilter}
//import org.grapheco.lynx.types.composite.LynxMap
//import org.grapheco.lynx.types.structural.{LynxNode, LynxRelationship}
//import org.opencypher.v9_0.expressions.{Expression, LabelName, LogicalVariable, NodePattern, RelTypeName, RelationshipPattern, SemanticDirection}
//import org.opencypher.v9_0.util.symbols.{CTNode, CTRelationship}
//import org.grapheco.lynx.types.structural.{LynxRelationshipType, LynxNodeLabel,LynxPropertyKey}
//
//
//case class LynxJDBCPPTExpandPath(override val rel: RelationshipPattern, override val rightNode: NodePattern)
//                                (implicit in: PPTNode, override val plannerContext: PhysicalPlannerContext)
//  extends PPTExpandPath(rel, rightNode)(in, plannerContext) {
//
//  override val children: Seq[PPTNode] = Seq(in)
//
//  override def withChildren(children0: Seq[PPTNode]): PPTExpandPath = PPTExpandPath(rel, rightNode)(children0.head, plannerContext)
//
//  override val schema: Seq[(String, LynxType)] = {
//    val RelationshipPattern(
//    variable: Option[LogicalVariable],
//    types: Seq[RelTypeName],
//    length: Option[Option[Range]],
//    properties: Option[Expression],
//    direction: SemanticDirection,
//    legacyTypeSeparator: Boolean,
//    baseRel: Option[LogicalVariable]) = rel
//    val NodePattern(var2, labels2: Seq[LabelName], properties2: Option[Expression], baseNode2: Option[LogicalVariable]) = rightNode
//    val schema0 = Seq(variable.map(_.name).getOrElse(s"__RELATIONSHIP_${rel.hashCode}") -> CTRelationship,
//      var2.map(_.name).getOrElse(s"__NODE_${rightNode.hashCode}") -> CTNode)
//    in.schema ++ schema0
//  }
//
//  //  override def execute(implicit ctx: ExecutionContext): DataFrame = {
//  //    val df = in.execute(ctx)
//  //    val RelationshipPattern(
//  //    variable: Option[LogicalVariable],
//  //    types: Seq[RelTypeName],
//  //    length: Option[Option[Range]],
//  //    properties: Option[Expression],
//  //    direction: SemanticDirection,
//  //    legacyTypeSeparator: Boolean,
//  //    baseRel: Option[LogicalVariable]) = rel
//  //    val NodePattern(var2, labels2: Seq[LabelName], properties2: Option[Expression], baseNode2: Option[LogicalVariable]) = rightNode
//  //
//  //    val schema0 = Seq(variable.map(_.name).getOrElse(s"__RELATIONSHIP_${rel.hashCode}") -> CTRelationship,
//  //      var2.map(_.name).getOrElse(s"__NODE_${rightNode.hashCode}") -> CTNode)
//  //
//  //    implicit val ec = ctx.expressionContext
//  //
//  //    DataFrame(df.schema ++ schema0, () => {
//  //      df.records.flatMap {
//  //        record0 =>
//  //          graphModel.expand(
//  //            record0.last.asInstanceOf[LynxNode].id,
//  //            RelationshipFilter(types.map(_.name).map(LynxRelationshipType), properties.map(eval(_).asInstanceOf[LynxMap].value.map(kv => (LynxPropertyKey(kv._1), kv._2))).getOrElse(Map.empty)),
//  //            NodeFilter(labels2.map(_.name).map(LynxNodeLabel), properties2.map(eval(_).asInstanceOf[LynxMap].value.map(kv => (LynxPropertyKey(kv._1), kv._2))).getOrElse(Map.empty)),
//  //            direction)
//  //            .map(triple =>
//  //              record0 ++ Seq(triple.storedRelation, triple.endNode))
//  //            .filter(item => {
//  //              //(m)-[r]-(n)-[p]-(t), r!=p
//  //              val relIds = item.filter(_.isInstanceOf[LynxRelationship]).map(_.asInstanceOf[LynxRelationship].id)
//  //              relIds.size == relIds.toSet.size
//  //            })
//  //      }
//  //    })
//  //  }
//  override def execute(implicit ctx: ExecutionContext): DataFrame = {
//    val df = in.execute(ctx)
//    val RelationshipPattern(
//    variable: Option[LogicalVariable],
//    types: Seq[RelTypeName],
//    length: Option[Option[Range]],
//    properties: Option[Expression],
//    direction: SemanticDirection,
//    legacyTypeSeparator: Boolean,
//    baseRel: Option[LogicalVariable]) = rel
//    val NodePattern(var2, labels2: Seq[LabelName], properties2: Option[Expression], baseNode2: Option[LogicalVariable]) = rightNode
//
//    val schema0 = Seq(variable.map(_.name).getOrElse(s"__RELATIONSHIP_${rel.hashCode}") -> CTRelationship,
//      var2.map(_.name).getOrElse(s"__NODE_${rightNode.hashCode}") -> CTNode)
//
//    implicit val ec = ctx.expressionContext
//
//    DataFrame(df.schema ++ schema0, () => {
//      df.records.flatMap {
//        record0 =>
//          graphModel.expand(
//            record0.last.asInstanceOf[LynxNode].id,
//            RelationshipFilter(types.map(_.name).map(LynxRelationshipType), properties.map(eval(_).asInstanceOf[LynxMap].value.map(kv => (LynxPropertyKey(kv._1), kv._2))).getOrElse(Map.empty)),
//            NodeFilter(labels2.map(_.name).map(LynxNodeLabel), properties2.map(eval(_).asInstanceOf[LynxMap].value.map(kv => (LynxPropertyKey(kv._1), kv._2))).getOrElse(Map.empty)),
//            direction)
//            .map(triple =>
//              record0 ++ Seq(triple.storedRelation, triple.endNode))
//            .filter(item => {
//              //(m)-[r]-(n)-[p]-(t), r!=p
//              val relIds = item.filter(_.isInstanceOf[LynxRelationship]).map(_.asInstanceOf[LynxRelationship].id)
//              relIds.size == relIds.toSet.size
//            })
//      }
//    })
//  }
//
//}
