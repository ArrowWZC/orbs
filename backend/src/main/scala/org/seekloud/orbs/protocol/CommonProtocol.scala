package org.seekloud.orbs.protocol

/**
  * User: TangYaruo
  * Date: 2018/10/10
  * Time: 16:49
  */
object CommonProtocol {

  trait Request

  trait Response {
    val errCode: Int
    val msg: String
  }

  case class CommonRsp(errCode: Int = 0, msg: String = "ok") extends Response

  val SuccessRsp = CommonRsp()

  val SignatureError = CommonRsp(1000001, "signature error.")

  val RequestTimeout = CommonRsp(1000003, "request timestamp is too old.")

  val AppClientIdError = CommonRsp(1000002, "appClientId error.")

  def internalError(message: String) = CommonRsp(1000004, s"internal error:$message")


}
