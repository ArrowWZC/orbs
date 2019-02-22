package org.seekloud.orbs.models.dao

import org.seekloud.utils.DBUtil.db
import slick.jdbc.PostgresProfile.api._
import org.seekloud.orbs.Boot.executor

import scala.concurrent.Future

/**
  * User: TangYaruo
  * Date: 2019/2/22
  * Time: 14:40
  */

case class UserInfo(playerId: String, nickname: String, password: String)

trait UserInfoTable {
  import org.seekloud.utils.DBUtil.driver.api._


  class UserInfoTable(tag: Tag) extends Table[UserInfo](tag, "USER_INFO") {
    def * = (playerId, nickname, password) <> (UserInfo.tupled, UserInfo.unapply)

    val playerId = column[String]("PLAYER_ID", O.PrimaryKey)
    val nickname = column[String]("NICKNAME")
    val password = column[String]("PASSWORD")

    /** Uniqueness Index over (userId) (database name user_info_user_id_index) */
    val index1 = index("TABLE_NAME_NICKNAME_UINDEX", nickname, unique=true)
    val index2 = index("USER_INFO_PLAYER_ID_uindex", playerId, unique=true)

  }

  protected val userInfoTableQuery = TableQuery[UserInfoTable]

}

object UserInfoDao extends UserInfoTable {

  def addUser(userInfo: UserInfo): Future[Int] =
    db.run(userInfoTableQuery += userInfo)

  def getAllUser: Future[Seq[UserInfo]] = {
    db.run(userInfoTableQuery.result)
  }


}
