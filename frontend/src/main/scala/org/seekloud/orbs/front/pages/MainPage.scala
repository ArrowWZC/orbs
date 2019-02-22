package org.seekloud.orbs.front.pages

import org.seekloud.orbs.front.common.{Page, PageSwitcher}
import mhtml.{Cancelable, Rx, Var, mount}
import org.scalajs.dom
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}


import scala.xml.Elem

object MainPage extends PageSwitcher {

  val pageWidth: Double = dom.window.innerWidth
  val pageHeight: Double = dom.window.innerHeight


  private val currentPage: Rx[Elem] = currentHashVar.map {
    case "entry" :: Nil => EntryPage.render
    case "signIn" :: Nil => SignInPage.render
    case _ => println(s"error in switch:$currentHashVar"); SignInPage.render
  }


  def show(): Cancelable = {
    val page =
      <div>
        {currentPage}
      </div>
    mount(dom.document.body, page)
  }

}
