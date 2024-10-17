package db

import graphModel.JDBCGraphModel
import org.grapheco.lynx.LynxResult
import org.grapheco.lynx.runner.{CypherRunner, GraphModel}
import schema.{Schema, SchemaManager}

class LynxJDBCConnector {
  object LynxJDBCConnector {

    // 带 schema 的连接方法
    def connect(url: String, username: String, password: String, schema: Schema): LynxJDBCConnector = {
      val db: DB = new DB(url, username, password)
      val graphModel: JDBCGraphModel = new JDBCGraphModel(db.connection, schema)
      val runner = new CypherRunner(graphModel)
      new LynxJDBCConnector(graphModel, runner, schema)
    }

    // 自动生成 schema 的连接方法
    def connect(url: String, username: String, password: String): LynxJDBCConnector = {
      val db: DB = new DB(url, username, password)
      val schema: Schema = SchemaManager.autoGeneration(db.connection)
      val graphModel: JDBCGraphModel = new JDBCGraphModel(db.connection, schema)
      val runner = new CypherRunner(graphModel)
      new LynxJDBCConnector(graphModel, runner, schema)
    }
  }

  // 数据库连接器类
  case class LynxJDBCConnector(graphModel: GraphModel, runner: CypherRunner, schema: Schema) {

    // 运行查询
    def run(query: String, param: Map[String, Any] = Map.empty): LynxResult = runner.run(query, param)
  }

}
