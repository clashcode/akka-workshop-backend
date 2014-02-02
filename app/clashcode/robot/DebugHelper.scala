package clashcode.robot

object DebugHelper {

  var firstDebug = true
  val candVari = new CandidateVariance()
  val sepa = "\t"

  def print(robots: Seq[Robot]) = {

    val generation = robots.map(_.code.generation).max

    val rf = robots(0)
    val gf = rf.code.generations
    val nf = rf.code.creatorName
    val pf = rf.points
    val rl = robots.last
    val nl = rl.code.creatorName
    val pl = rl.points
    //val vari = candVari.diffCount(robots)
    //println("%5d (%40s %5d) (%40s %5d) %5.3f" format (generation, nf, pf, nl, pl, vari))
    
    println("- %10d %10s %5d %30s - %s" format (generation, nf, pf, TopName.name(gf), rf.code.code.mkString("")))
  }

}
