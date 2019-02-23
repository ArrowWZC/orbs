package org.seekloud.orbs.shared.ptcl.protocol

/**
  * User: TangYaruo
  * Date: 2019/2/22
  * Time: 17:37
  */
object UserProtocol {

  trait Request

  trait Response {
    val errCode: Int
    val msg: String
  }

  //注册
  case class SignUpReq(nickname: String, password: String) extends Request

  case class SignUpRsp(
    playerId: Option[String] = None,
    errCode: Int = 0,
    msg: String = "ok"
  ) extends Response

  val NicknameInvalid = SignUpRsp(errCode = 100001, msg = "该用户名已被注册！")

  val SignUpFail = SignUpRsp(errCode = 100002, msg = "服务器出错，请重试！")


  //登录
  case class SignInReq(nickname: String, password: String) extends Request

  case class SignInRsp(
    playerId: Option[String] = None,
    errCode: Int = 0,
    msg: String = "ok"
  ) extends Response

  val NicknameError = SignInRsp(errCode = 100003, msg ="该用户名不存在！")

  val PasswordError = SignInRsp(errCode = 100004, msg = "密码错误！")

  val UserExist = SignInRsp(errCode = 100005, msg = "该用户已在游戏中！")



}
