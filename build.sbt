import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}
import xerial.sbt.Sonatype._


lazy val root = project.in(file("."))
  .aggregate(
    shironekoCoreJS,
    shironekoCoreJVM,
    shironekoSlinkyJS,
  )
  .settings(commonSettings ++ noCrossBuild ++ noPublish)
  .enablePlugins(MicrositesPlugin)
  .settings(
    micrositeName := "Shironeko",
    micrositeCompilingDocsTool := WithMdoc,
    micrositeGithubOwner := "oleg-py",
    micrositeGithubRepo := "shironeko",
    micrositeBaseUrl := "/shironeko",
    micrositeGitterChannel := false, // TODO - maaaaybee
    micrositeDataDirectory := { baseDirectory.value / "site" },
    micrositeAuthor := "Oleg Pyzhcov",
//    mdocJS := Some(jsdocs),
//    mdocJSLibraries := webpack.in(jsdocs, Compile, fullOptJS).value,
//    mdocVariables := Map("js-opt" -> "fast"),
  )

lazy val shironekoCoreJS = shironekoCore.js
lazy val shironekoCoreJVM = shironekoCore.jvm

lazy val shironekoCore = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("core"))
  .settings(commonSettings ++ noCrossBuild)
  .settings(
    name := "shironeko-core"
  )

lazy val shironekoSlinkyJS = shironekoSlinky.js

lazy val shironekoSlinky = crossProject(JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("slinky"))
  .dependsOn(shironekoCore)
  .settings(commonSettings ++ noCrossBuild)
  .settings(
    name := "shironeko-slinky",
    libraryDependencies += "me.shadaj" %%% "slinky-core" % "0.6.7",
  )

/*lazy val jsdocs = project
  .enablePlugins(ScalaJSBundlerPlugin)
  .dependsOn(shironekoSlinkyJS)
  .settings(commonSettings ++ noCrossBuild ++ noPublish)
  .settings(
    name := "shironeko-slinky-jsdocs",
    scalaJSUseMainModuleInitializer := true,

    npmDependencies in Compile ++= Seq(
      "react" -> "16.8.6",
      "react-dom" -> "16.8.6",
      "react-proxy" -> "1.1.8",
      "webpack-merge" -> "4.2.1",
    ),

    libraryDependencies ++= Seq(
      "me.shadaj" %%% "slinky-web" % "0.6.3",
    ),
    scalacOptions ++= Seq(
      "-P:scalajs:sjsDefinedByDefault",
      "-Ymacro-annotations",
    ),
    webpackBundlingMode := BundlingMode.LibraryOnly(),
    scalaJSModuleKind := ModuleKind.CommonJSModule,
//    scalaJSLinkerConfig ~= { _.withOptimizer(false) },
  )*/

/*lazy val todoMVC = project
  .in(file("todo-mvc"))
  .enablePlugins(ScalaJSBundlerPlugin)
  .dependsOn(shironekoSlinkyJS)
  .settings(commonSettings ++ noCrossBuild ++ noPublish)
  .settings(
    name := "shironeko-slinky-todomvc",
    resolvers += Resolver.bintrayRepo("oyvindberg", "ScalablyTyped"),

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

      "react-router-dom" -> "5.0.1",
      "todomvc-app-css" -> "2.2.0"
    ),

    libraryDependencies ++= Seq(
      "me.shadaj" %%% "slinky-web" % "0.6.4",
      "me.shadaj" %%% "slinky-hot" % "0.6.4",
      ScalablyTyped.R.`react-router-dom`,
      ScalablyTyped.R.`react-slinky-facade`,
    ),
    scalacOptions ++= Seq(
      "-P:scalajs:sjsDefinedByDefault",
      "-Ymacro-annotations",
    ),

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
  )*/

def noPublish = List(
  skip in publish := true,
  publish := {},
  publishLocal := {},
  publishArtifact := false,
  publishTo := None,
)

def noCrossBuild = List(
  scalaVersion := "2.13.5",
  crossScalaVersions := Seq("2.13.5"),
)

def commonSettings = List(
  name := "shironeko",
  organization := "com.olegpy",
  version := "0.2.0-RC1",

  resolvers += Resolver.sonatypeRepo("snapshots"),
  licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
  homepage := Some(url("http://github.com/oleg-py/shironeko")),

  libraryDependencies ++= Seq(
    scalaOrganization.value % "scala-reflect" % scalaVersion.value % "provided",
    scalaOrganization.value % "scala-compiler" % scalaVersion.value % "provided",

    "org.typelevel" %%% "cats-effect" % "3.0.0",
    "co.fs2"        %%% "fs2-core"    % "3.0.0",
    compilerPlugin("org.typelevel" % "kind-projector" % "0.11.3" cross CrossVersion.full),
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
