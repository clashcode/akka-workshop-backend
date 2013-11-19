package com.clashcode.web.controllers

import play.api.mvc._
import com.clashcode.web.views
import play.api.libs.iteratee.{Iteratee, Concurrent}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.Logger

object Application extends Controller {

  private val (out, channel) = Concurrent.broadcast[String]

  def push(message: String) = channel.push(message)

  def index = Action {
    implicit request =>
      val url = routes.Application.status().webSocketURL()
      Ok(views.html.index(url))
  }

  def status = WebSocket.using[String] { request =>

    Logger.info("new listener")

    // ignore incoming websocket traffic
    val in = Iteratee.foreach[String] {
      msg => Logger.debug(msg)
    } mapDone {
      _ => Logger.info("removed listener")
    }

    (in,out)
  }

}