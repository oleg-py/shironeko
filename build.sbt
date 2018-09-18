import xerial.sbt.Sonatype._


inThisBuild(Seq(
  organization := "com.olegpy",
  scalaVersion := "2.12.6",
  version := "0.0.1-SNAPSHOT",
  crossScalaVersions := Seq("2.11.12", "2.12.6"),
))

lazy val root = project.in(file("."))
  .aggregate(shironekoJVM, shironekoJS)
  .settings(commonSettings)
  .settings(
    skip in publish := true,
    publish := {},
    publishLocal := {},
    publishArtifact := false,
    publishTo := None,
  )

lazy val shironekoJS = shironeko.js
lazy val shironekoJVM = shironeko.jvm

lazy val shironeko = crossProject
  .crossType(CrossType.Pure)
  .in(file("."))
  .settings(commonSettings)

def commonSettings = List(
  name := "shironeko",

  licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
  homepage := Some(url("http://github.com/oleg-py/shironeko")),
  
  libraryDependencies ++= Seq(
    "org.typelevel" %%% "cats-effect" % "1.0.0",
    "co.fs2"        %%% "fs2-core"    % "1.0.0-M5",
  ),

  //testFrameworks += new TestFramework("minitest.runner.Framework"),
  scalacOptions --= Seq(
    "-Xfatal-warnings",
    "-Ywarn-unused:params",
    "-Ywarn-unused:implicits",
  ),

  publishTo := sonatypePublishTo.value,
  publishMavenStyle := true,
  sonatypeProjectHosting := Some(GitHubHosting("oleg-py", "shironeko", "oleg.pyzhcov@gmail.com")),
)