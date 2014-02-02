package clashcode.robot

object TopName {

  def name(map: Map[String, Int]): String = {
    def sorting(a: (String, Int), b:(String, Int)): Boolean = a._2 > b._2
    val hold = map.values.max.toDouble * 0.5
    val filtered = (map.toList).filter{case (k, v) => v > hold}
    val sorted = filtered.sortWith(sorting)
    sorted.map{case (k, v) => s"[$k]"}.mkString("")
  }

}

