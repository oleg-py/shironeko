val sjs1x = "1."
val scalaJSVersion = Option(System.getenv("SCALAJS_VERSION")).filter(_.nonEmpty).getOrElse("1.1.0")

resolvers += Resolver.bintrayRepo("oyvindberg", "converter")

addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat" % "0.1.12")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % scalaJSVersion)
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.0.0")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.5")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.2")
addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.4.0")
addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.1.0-M9")
addSbtPlugin("com.typesafe.sbt"          % "sbt-git"              % "0.9.3")

if(scalaJSVersion startsWith sjs1x) {
  addSbtPlugin("ch.epfl.scala" % "sbt-scalajs-bundler" % "0.18.0")
  addSbtPlugin("org.scalablytyped.converter" % "sbt-converter" % "1.0.0-beta15")
} else {
  addSbtPlugin("ch.epfl.scala" % "sbt-scalajs-bundler-sjs06" % "0.18.0")
  addSbtPlugin("org.scalablytyped.converter" % "sbt-converter06" % "1.0.0-beta15")
}
addSbtPlugin("com.47deg"  % "sbt-microsites" % "0.9.7")
