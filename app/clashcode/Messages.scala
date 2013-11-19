package clashcode

/** [Send] Identify yourself with a username of your choice (max 12 chars) */
case class Hello(name: String)

/** [Receive] Prisoner with given name requests an answer: do you cooperate or defect? */
case class PrisonerRequest(name: String)

/** [Send] Answer to the game request: cooperate or defect. */
case class PrisonerResponse(cooperate: Boolean)

