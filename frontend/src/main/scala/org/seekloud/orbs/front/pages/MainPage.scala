package org.seekloud.orbs.front.pages

import org.seekloud.orbs.front.common.{Page, PageSwitcher}
import mhtml.{Cancelable, Rx, Var, mount}
import org.scalajs.dom

import scala.xml.Elem


object MainPage extends PageSwitcher {



  private val currentPage: Rx[Elem] = currentHashVar.map {
    case "home" :: Nil => OrbsRender.render
    case _ => <div>Error Page</div>
  }


  def show(): Cancelable = {
    val page =
      <div>
        {currentPage}
      </div>
    mount(dom.document.body, page)
  }

}