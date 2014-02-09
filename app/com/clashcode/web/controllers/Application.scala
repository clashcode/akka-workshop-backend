package com.clashcode.web.controllers

import play.api.mvc._
import com.clashcode.web.views
import play.api.libs.iteratee.{Iteratee, Concurrent}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.{Play, Logger}
import play.api.libs.json.{Json, JsValue}
import actors.{ResetStats, Player}
import akka.actor.ActorRef
import clashcode.robot.Robot
import Play.current

object Application extends Controller {

  private val (out, channel) = Concurrent.broadcast[JsValue]

  var maybeHostingActor = Option.empty[ActorRef]

  def push(players: Seq[Player]) = {
    channel.push(
      Json.obj("players" ->
        Json.toJson(players.map(p =>
          Json.obj(
            "name" -> p.name,
            "robots" -> p.robots,
            "best" -> p.best.map(robotToJson(_)),
            "lastSeen" -> p.lastSeen,
            "status" -> p.status)))))
  }

  private def robotToJson(robot: Robot) = {
    Json.obj(
      "points" -> robot.points,
      "code" -> Json.obj(
        "code" -> robot.code.code.mkString,
        "creatorName" -> robot.code.creatorName,
        "generation" -> robot.code.generation,
        "generations" -> robot.code.generations.toSeq.map {
          case (name, count) => Json.obj(
            "name" -> name,
            "count" -> count
          )
        }
      )
    )
  }

  def push(message: String) = channel.push(Json.obj("status" -> message))

  def index = Action {
    implicit request =>
      val url = routes.Application.status().webSocketURL()
      val myIp = Play.configuration.getString("cluster.akka.remote.netty.tcp.hostname").getOrElse("localhost")
      val myPort = Play.configuration.getString("cluster.akka.remote.netty.tcp.port").getOrElse("localhost")
      val host = "akka.tcp://cluster@" + myIp + ":" + myPort
      Ok(views.html.index(url, host))
  }

  def status = WebSocket.using[JsValue] { request =>

    Logger.info("new listener")

    // ignore incoming websocket traffic
    val in = Iteratee.foreach[JsValue] {
      msg =>

        Logger.debug(msg.toString)
        val action = (msg \ "action").asOpt[String].getOrElse("")

        // reset stats command
        if (action == "reset")
          maybeHostingActor.foreach(_ ! ResetStats)

    } mapDone {
      _ => Logger.info("removed listener")
    }

    (in,out)
  }

}


