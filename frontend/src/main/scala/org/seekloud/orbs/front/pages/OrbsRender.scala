package org.seekloud.orbs.front.pages

import org.seekloud.orbs.front.common.{Page, Routes}
import org.seekloud.orbs.front.utils.{JsFunc, Shortcut}
import org.seekloud.orbs.shared.ptcl.model.Point
import mhtml.Var
import org.scalajs.dom
import org.scalajs.dom.ext.Color
import org.scalajs.dom.html.{Canvas, Input}
import mhtml._
import org.seekloud.orbs.front.orbs.{GameHolder, GameHolder4Play}
import org.seekloud.orbs.shared.ptcl.model.Constants.GameState

import scala.xml.Elem

/**
  * Created by Jingyi on 2018/11/9
  */
class OrbsRender(playerName: String, playerId: Option[String] = None) extends Page {

  //  val pageWidth: Double = dom.window.innerWidth
  //  val pageHeight: Double = dom.window.innerHeight

  private var gameHolder4Play: Option[GameHolder4Play] = None

  private val myCanvasName = "myView"
  private val opCanvasName = "opView"


  private val canvas = <canvas id={myCanvasName} class="canvas1" tabindex="1"></canvas>
  private val opCanvas = <canvas id={opCanvasName} class="canvas2" tabindex="1"></canvas>

  /*emoji*/
  private val  flower = <img id="flower" src={Routes.imgPath("flower.png")} class="detail" onclick={() => showEmoji := 0; setToOpValue("/u0001")}></img>
  private val egg = <img id="egg" src={Routes.imgPath("egg.png")} class="detail"  onclick={() => showEmoji := 0; setToOpValue("/u0002")}></img>
  private val shit = <img id="shit" src={Routes.imgPath("shit.png")} class="detail" onclick={() => showEmoji := 0; setToOpValue("/u0003")}></img>
  private val coffee = <img id="coffee" src={Routes.imgPath("coffee.png")} class="detail" onclick={() => showEmoji := 0; setToOpValue("/u0004")}></img>
  private val good = <img id="good" src={Routes.imgPath("good.png")} class="detail" onclick={() => showEmoji := 0; setToOpValue("/u0005")}></img>
  private val face1 = <img id="good" src={Routes.imgPath("face1.png")} class="detail" onclick={() => showEmoji := 0; setToOpValue("/u0006")}></img>
  private val face2 = <img id="good" src={Routes.imgPath("face2.png")} class="detail" onclick={() => showEmoji := 0; setToOpValue("/u0007")}></img>
  private val face3 = <img id="good" src={Routes.imgPath("face3.png")} class="detail" onclick={() => showEmoji := 0; setToOpValue("/u0008")}></img>
//  private val comon = <img id="good" src={Routes.imgPath("comon.png")} class="detail" onclick={() => showEmoji := 0; setToOpValue("/u0009")}></img>
//  private val low = <img id="good" src={Routes.imgPath("low.png")} class="detail" onclick={() => showEmoji := 0; setToOpValue("/u0010")}></img>


  private def setToOpValue(info: String): Unit = {
    val toOpValue = dom.document.getElementById("toOp").asInstanceOf[Input].value
    dom.document.getElementById("toOp").asInstanceOf[Input].value = toOpValue + info
  }

  private val showEmoji = Var(0)
  private val emojiArea = showEmoji.map {
    case 0 =>
      emptyHTML
    case 1 =>
      <div class="emoji">
        {flower}
        {egg}
        {shit}
        {coffee}
        {good}
        {face1}
        {face2}
        {face3}
      </div>
  }

  private val interactArea =
    <div class="interact">
      <div class="input-group">
        <input id="toOp" type="text" class="form-control" aria-label="Text input with multiple buttons" placeholder="给对手说点什么吧..."/>
          <div class="input-group-btn">
            <!-- Buttons -->
            <button type="button" class="btn btn-default" aria-label="Help" onclick={() => showEmoji := 1}>😊</button>
            <button type="button" class="btn btn-default" onclick={() => sendToOp()}>发送</button>
          </div>
        </div>
    </div>

  private def sendToOp() = {
    val info = dom.document.getElementById("toOp").asInstanceOf[Input].value
    gameHolder4Play.foreach { gameHolder =>
      if (gameHolder.gameState == GameState.play) {
        gameHolder.sendInfo(info)
      } else {
        JsFunc.alert("游戏开始后再发送哦~")
      }
    }
  }


  private val topArea = playerId match {
    case Some(_) =>
      <div class="container topArea">
        <div class="row">
          {interactArea}
          {emojiArea}
        </div>
      </div>
    case None => emptyHTML

  }

  private val gameInstrument =
    <div class="gameInstrument" style={s"background:url(${Routes.imgPath("gi.png")});" +
                                       s"background-size:cover;" +
                                       s"width:300px;" +
                                       s"height:246px"}>
      <p id="ins" class="text">
        游戏说明:<br/>
        点击鼠标左键发球；<br/>
        键盘控制接球版移动；<br/>
        首先打完砖块者胜利；<br/>
        用光生命直接死亡；<br/>
        砖块下落到地步直接死亡<br/>
      </p>
    </div>

  def init(): Unit = {
    println(s"OrbsRender init...")
//    dom.document.getElementById("ins").innerHTML = "游戏说明:\n" +
//                                                   "点击鼠标左键发球；\n" +
//                                                   "键盘控制接球版移动；\n" +
//                                                   "首先打完砖块者胜利；\n" +
//                                                   "用光生命直接死亡\n" +
//                                                   "砖块下落到地步直接死亡"
    val gameHolder = new GameHolder4Play(myCanvasName, opCanvasName)
    gameHolder.start(playerName, playerId)
    gameHolder4Play = Some(gameHolder)
  }


  override def render: Elem = {
    Shortcut.scheduleOnce(() => init(), 0)
    <div style={s"background:url(${Routes.imgPath("pageBack.jpg")});background-size:cover;" +
                s"width:${MainPage.pageWidth}px;height:${MainPage.pageHeight}px"}>
      {topArea}
      {canvas}
      {opCanvas}
      {gameInstrument}
    </div>
  }


}
