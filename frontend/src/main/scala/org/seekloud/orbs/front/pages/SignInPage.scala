package org.seekloud.orbs.front.pages

import org.scalajs.dom
import org.scalajs.dom.html.Input
import org.seekloud.orbs.front.common.{Page, Routes}
import org.seekloud.orbs.front.utils.{Http, JsFunc, Shortcut}
import org.seekloud.orbs.shared.ptcl.protocol.UserProtocol
import io.circe.syntax._
import io.circe.generic.auto._
import mhtml.{Rx, Var}

import scala.xml.Elem
import scala.concurrent.ExecutionContext.Implicits.global


/**
  * User: TangYaruo
  * Date: 2019/2/22
  * Time: 20:19
  */
object SignInPage extends Page {

  val defaultName: String = dom.window.localStorage.getItem("username")
  val defaultPsw: String = dom.window.localStorage.getItem("password")


  private val header =
    <div class="ai-road-account-header">
      <div class="ai-road-container">
        <a href={locationHashString}>魔法球打砖块PK</a>
        <span class="ai-road-account-language">Orbs</span>
      </div>
    </div>

  private val section =
    <div class="ai-road-account-section">
      <div class="ai-road-account-side">
        <img src={Routes.imgPath("sign_banner.jpg")} class="ai-road-account-banner"/>
      </div>
      <div class="ai-road-account-form">
        <div class="ai-road-account-title">登录</div>
        <div class="ai-road-account-form-item">
          <span class="ai-road-account-required"></span>
          <input id="username" class="ai-road-account-input" type="text" placeholder="账号" value={s"$defaultName"}/>
        </div>
        <div class="ai-road-account-form-item">
          <span class="ai-road-account-required"></span>
          <input id="password" class="ai-road-account-input" type="password" placeholder="密码" value={s"$defaultPsw"}/>
        </div>
        <button class="ai-road-account-btn" onclick={() =>
          signIn()
          ()}>登录</button>
        <button class="ai-road-account-btn" onclick={() =>
          signUp()
          ()}>注册</button>
      </div>
    </div>

  private val footer =
    <div class="ai-road-account-footer">
      Arrow版权所有
    </div>

  private val userForm =
    <div class="ai-road-account">
      {header}{section}{footer}
    </div>

  private def signUp() = {
    val username = dom.document.getElementById("username").asInstanceOf[Input].value
    val password = dom.document.getElementById("password").asInstanceOf[Input].value
    if (username.nonEmpty && password.nonEmpty) {
      val data = UserProtocol.SignUpReq(username, password).asJson.noSpaces
      Http.postJsonAndParse[UserProtocol.SignUpRsp](Routes.signUp, data).map {
        rsp =>
          rsp.errCode match {
            case 0 =>
              JsFunc.alert(s"注册成功！")
              val playerId = rsp.playerId.get
              dom.window.localStorage.setItem("username", username)
              dom.window.localStorage.setItem("password", password)
              dom.window.localStorage.setItem("player_id", playerId)
              showGame := 1
            case _ =>
              JsFunc.alert(rsp.msg)
          }
      }
    } else {
      JsFunc.alert("输入不能为空!")
    }
  }

  private def signIn() = {
    val username = dom.document.getElementById("username").asInstanceOf[Input].value
    val password = dom.document.getElementById("password").asInstanceOf[Input].value
    if (username.nonEmpty && password.nonEmpty) {
      val data = UserProtocol.SignInReq(username, password).asJson.noSpaces
      Http.postJsonAndParse[UserProtocol.SignInRsp](Routes.signIn, data).map {
        rsp =>
          rsp.errCode match {
            case 0 =>
              JsFunc.alert(s"登陆成功！")
              val playerId = rsp.playerId.get
              dom.window.localStorage.setItem("username", username)
              dom.window.localStorage.setItem("password", password)
              dom.window.localStorage.setItem("player_id", playerId)
              showGame := 1
            case _ =>
              JsFunc.alert(rsp.msg)
          }
      }
    } else {
      JsFunc.alert("输入不能为空!")
    }
  }

  val showGame = Var(0)
  val show: Rx[Elem] = showGame.map{
    case 0 =>
      userForm

    case 1 =>
      val pName = dom.window.localStorage.getItem("username")
      val pId= dom.window.localStorage.getItem("player_id")
      new OrbsRender(pName, Some(pId)).render
  }


  override def render: Elem =
    <div>
      {show}
    </div>

}
