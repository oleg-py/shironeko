import xerial.sbt.Sonatype._
import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

inThisBuild(Seq(
  organization := "com.olegpy",
  scalaVersion := "2.12.8",
  version := "0.1.0-M1",
  crossScalaVersions := Seq("2.12.8"),
))

lazy val root = project.in(file("."))
  .aggregate(
    shironekoCoreJS,
    shironekoCoreJVM,
    shironekoSlinkyJS,
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

lazy val shironekoSlinkyJS = shironekoSlinky.js

lazy val shironekoSlinky = crossProject(JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("slinky"))
  .dependsOn(shironekoCore)
  .settings(commonSettings)
  .settings(
    name := "shironeko-slinky",
    libraryDependencies += "me.shadaj" %%% "slinky-core" % "0.6.1",
    scalacOptions += "-P:scalajs:sjsDefinedByDefault",
  )

lazy val todoMVC = project
  .in(file("todo-mvc"))
  .enablePlugins(ScalaJSBundlerPlugin)
  .settings(commonSettings)
  .dependsOn(shironekoSlinkyJS)
  .settings(commonSettings)
  .settings(
    name := "shironeko-slinky-todomvc",

    npmDependencies in Compile ++= Seq(
      "react" -> "16.8.6",
      "react-dom" -> "16.8.6",
      "react-proxy" -> "1.1.8",

      "file-loader" -> "3.0.1",
      "style-loader" -> "0.23.1",
      "css-loader" -> "2.1.1",
      "html-webpack-plugin" -> "3.2.0",
      "copy-webpack-plugin" -> "5.0.2",
      "webpack-merge" -> "4.2.1",

      "todomvc-app-css" -> "2.2.0"
    ),

    libraryDependencies ++= Seq(
      "me.shadaj" %%% "slinky-web" % "0.6.0",
      "me.shadaj" %%% "slinky-hot" % "0.6.0",
      "io.monix" %%% "monix-eval" % "3.0.0-RC3",
    ),
    scalacOptions += "-P:scalajs:sjsDefinedByDefault",
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full),

    version in webpack := "4.29.6",
    version in startWebpackDevServer:= "3.2.1",
    webpackResources := baseDirectory.value / "webpack" * "*",
    webpackConfigFile in fastOptJS := Some(baseDirectory.value / "webpack" / "webpack-fastopt.config.js"),
    webpackConfigFile in fullOptJS := Some(baseDirectory.value / "webpack" / "webpack-opt.config.js"),
    webpackConfigFile in Test := Some(baseDirectory.value / "webpack" / "webpack-core.config.js"),
    webpackDevServerExtraArgs in fastOptJS := Seq("--inline", "--hot"),
    webpackBundlingMode in fastOptJS := BundlingMode.LibraryOnly(),

    addCommandAlias("dev", ";fastOptJS::startWebpackDevServer;~fastOptJS"),
    addCommandAlias("build", "fullOptJS::webpack"),
  )

def commonSettings = List(
  name := "shironeko",

  resolvers += Resolver.sonatypeRepo("snapshots"),
  licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
  homepage := Some(url("http://github.com/oleg-py/shironeko")),

  libraryDependencies ++= Seq(
    "org.typelevel" %%% "cats-effect" % "1.3.1",
    "co.fs2"        %%% "fs2-core"    % "1.0.5",
    compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
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
