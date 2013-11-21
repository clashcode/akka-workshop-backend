package com.clashcode.web.controllers

import play.api.mvc._
import com.clashcode.web.views
import play.api.libs.iteratee.{Iteratee, Concurrent}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.Logger
import play.api.libs.json.{Json, JsValue}
import actors.{Player, Game}

object Application extends Controller {

  private val (out, channel) = Concurrent.broadcast[JsValue]

  def push(game: Game) = channel.push(
    Json.obj("game" ->
      Json.toJson(game.turns.map(t =>
        Json.obj(
          "name" -> t.player.name,
          "cooperate" -> t.cooperate,
          "points" -> t.points)))))

  def push(players: Seq[Player]) = channel.push(
    Json.obj("players" ->
      Json.toJson(players.map(p =>
        Json.obj(
          "name" -> p.name,
          "active" -> p.active,
          "cluster" -> p.cluster,
          "coop" -> p.coop,
          "games" -> p.games,
          "lastSeen" -> p.lastSeen,
          "ping" -> p.ping,
          "points" -> p.points)))))


  def push(message: String) = channel.push(Json.obj("status" -> message))

  def index = Action {
    implicit request =>
      val url = routes.Application.status().webSocketURL()
      Ok(views.html.index(url))
  }

  def status = WebSocket.using[JsValue] { request =>

    Logger.info("new listener")

    // ignore incoming websocket traffic
    val in = Iteratee.foreach[JsValue] {
      msg => Logger.debug(msg.toString)
    } mapDone {
      _ => Logger.info("removed listener")
    }

    (in,out)
  }

}


