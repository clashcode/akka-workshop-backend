package com.clashcode.web.controllers

import play.api.mvc._
import com.clashcode.web.views
import play.api.libs.iteratee.{Enumerator, Iteratee, Concurrent}
import play.api.libs.concurrent.Execution.Implicits._

object Application extends Controller {

  var maybeChannel : Option[Concurrent.Channel[String]] = None

  def index = Action {
    Ok(views.html.index("2 Your new application is ready."))
  }

  def status = WebSocket.using[String] { request =>

  //Concurernt.broadcast returns (Enumerator, Concurrent.Channel)
    val (out,channel) = Concurrent.broadcast[String]
    maybeChannel = Some(channel)

    //log the message to stdout and send response back to client
    val in = Iteratee.foreach[String] {
      msg => println(msg)
        //the Enumerator returned by Concurrent.broadcast subscribes to the channel and will
        //receive the pushed messages
        channel push("RESPONSE: " + msg)
        channel.eofAndEnd()
    }
    (in,out)
  }

}