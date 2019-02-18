package org.seekloud.orbs.front.pages

import mhtml.Var
import org.scalajs.dom
import org.scalajs.dom.html.Input
import org.seekloud.orbs.front.common.Page

import scala.xml.Elem

/**
  * User: TangYaruo
  * Date: 2019/2/15
  * Time: 21:17
  */
object EntryPage extends Page {
  val defaultName: String =
    if(dom.window.localStorage.getItem("entry_nickname") == null)
      ""
    else
      dom.window.localStorage.getItem("entry_nickname")

  val showGame = Var(0)
  val show = showGame.map{
    case 0 =>
      <div>
        <div class="entry">
          <div class="title">
            <h1>Orbs</h1>
          </div>
          <div class="text">
            <input type="text" class="form-control" id="userName" placeholder="nickname" value={s"$defaultName"}></input>
          </div>
          <div class="button">
            <button type="button" class="btn" onclick={()=>joinGame()}>join</button>
          </div>
        </div>
      </div>

    case 1 =>
      val pName = dom.document.getElementById("userName").asInstanceOf[Input].value
      new OrbsRender(pName).render
  }

  def joinGame(): Unit = {
    println("joinGame...")
    val userName = dom.document.getElementById("userName").asInstanceOf[Input].value
    dom.window.localStorage.setItem("entry_nickname", userName)
    showGame := 1
  }



  override def render: Elem = {
    <div>
      {show}
    </div>
  }

}
