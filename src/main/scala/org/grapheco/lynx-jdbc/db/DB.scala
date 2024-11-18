package db


import com.typesafe.scalalogging.LazyLogging

import java.sql.{Connection, DriverManager}

class DB(val url:String,val userName:String,val password: String) extends LazyLogging {
  val driver ="com.mysql.cj.jdbc.Driver"
  Class.forName(driver)

  def connection:Connection =DriverManager.getConnection(url,userName,password)
}
