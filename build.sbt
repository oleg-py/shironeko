import xerial.sbt.Sonatype._
import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

inThisBuild(Seq(
  organization := "com.olegpy",
  scalaVersion := "2.12.8",
  version := "0.1.0-SNAPSHOT",
  crossScalaVersions := Seq("2.12.8"),
))

lazy val root = project.in(file("."))
  .aggregate(
    shironekoCoreJS,
    shironekoCoreJVM,
    shironekoSlinkyJS,
    shironekoUtilJS,
    shironekoUtilJVM,
  )
  .settings(commonSettings)
  .settings(
    skip in publish := true,
    publish := {},
    publishLocal := {},
    publishArtifact := false,
    publishTo := None,
  )

lazy val shironekoCoreJS = shironekoCore.js
lazy val shironekoCoreJVM = shironekoCore.jvm

lazy val shironekoCore = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("core"))
  .settings(commonSettings)
  .settings(
    name := "shironeko-core"
  )

lazy val shironekoUtilJS = shironekoUtil.js
lazy val shironekoUtilJVM = shironekoUtil.jvm

lazy val shironekoUtil = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("util"))
  .settings(commonSettings)
  .settings(
    name := "shironeko-util",
    libraryDependencies += "com.chuusai" %%% "shapeless" % "2.3.3"
  )

lazy val shironekoSlinkyJS = shironekoSlinky.js

lazy val shironekoSlinky = crossProject(JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("slinky"))
  .settings(commonSettings)
  .settings(
    name := "shironeko-slinky",
    libraryDependencies += "me.shadaj" %%% "slinky-core" % "0.6.0",
    scalacOptions += "-P:scalajs:sjsDefinedByDefault",
  )

def commonSettings = List(
  name := "shironeko",

  resolvers += Resolver.sonatypeRepo("snapshots"),
  licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
  homepage := Some(url("http://github.com/oleg-py/shironeko")),

  libraryDependencies ++= Seq(
    "org.typelevel" %%% "cats-effect" % "1.2.0",
    "co.fs2"        %%% "fs2-core"    % "1.0.5-SNAPSHOT",
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
