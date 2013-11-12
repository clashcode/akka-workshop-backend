package com.clashcode.web.controllers

import play.api.mvc._
import com.clashcode.web.views
import play.api.libs.iteratee.{Iteratee, Concurrent}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.Logger

object Application extends Controller {

  var channels = Seq.empty[Concurrent.Channel[String]]

  def push(message: String) = channels.foreach(_.push(message))

  def index = Action {
    implicit request =>
      val url = routes.Application.status().webSocketURL()
      Ok(views.html.index(url))
  }

  def status = WebSocket.using[String] { request =>

    val (out,channel) = Concurrent.broadcast[String]
    channels = channels :+ channel
    Logger.info("added channel: " + channels.length.toString)

    val in = Iteratee.foreach[String] {
      msg =>
        println(msg)
        channel push("RESPONSE: " + msg)
        channel.eofAndEnd()
    } mapDone {
      _ =>
        channels = channels.filter(_ != channel)
        Logger.info("removed channel: " + channels.length.toString)
    }


    (in,out)
  }

}