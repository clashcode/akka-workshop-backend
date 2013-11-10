package com.clashcode.web.controllers

import play.api._
import play.api.mvc._
import com.clashcode.web.views

object Application extends Controller {

  def index = Action {
    Ok(views.html.index("2 Your new application is ready."))
  }

}