import sbt.Keys.scalacOptions
import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}
import xerial.sbt.Sonatype._

lazy val scala212 = "2.12.11"
lazy val scala213 = "2.13.2"
lazy val supportedScalaVersions = List(scala212, scala213)
val customScalaJSVersion = Option(System.getenv("SCALAJS_VERSION"))

lazy val root = project.in(file("."))
  .aggregate(
    shironekoCoreJS,
    shironekoCoreJVM,
    shironekoSlinkyJS,
  )
  .settings(commonSettings ++ crossBuild ++ noPublish)
  .enablePlugins(MicrositesPlugin)
  .settings(
    crossScalaVersions := Nil,
    micrositeName := "Shironeko",
    micrositeCompilingDocsTool := WithMdoc,
    micrositeGithubOwner := "oleg-py",
    micrositeGithubRepo := "shironeko",
    micrositeBaseUrl := "/shironeko",
    micrositeGitterChannel := false, // TODO - maaaaybee
    micrositeDataDirectory := { baseDirectory.value / "site" },
    micrositeAuthor := "Oleg Pyzhcov",
    mdocJS := Some(jsdocs),
    mdocJSLibraries := webpack.in(jsdocs, Compile, fullOptJS).value,
//    mdocVariables := Map("js-opt" -> "fast"),
  )

lazy val shironekoCoreJS = shironekoCore.js
lazy val shironekoCoreJVM = shironekoCore.jvm

lazy val shironekoCore = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("core"))
  .settings(commonSettings ++ crossBuild)
  .settings(
    name := "shironeko-core",
).jvmSettings(
  skip.in(publish) := customScalaJSVersion.forall(_.startsWith("1.")) // avoid to publish multiple times for jvm
)

lazy val commonNpmDependencies = Seq(
  "react" -> "16.8.6",
  "react-dom" -> "16.8.6",
  "react-proxy" -> "1.1.8",
  "webpack-merge" -> "4.2.2",
)

lazy val shironekoSlinkyJS = shironekoSlinky.js

lazy val shironekoSlinky = crossProject(JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("slinky"))
  .dependsOn(shironekoCore)
  .settings(commonSettings ++ crossBuild)
  .settings(
    name := "shironeko-slinky",
    libraryDependencies += "me.shadaj" %%% "slinky-core" % "0.6.5",
    scalacOptions ++= { if (scalaJSVersion.startsWith("0.6.")) Seq("-P:scalajs:sjsDefinedByDefault") else Nil }
  )

lazy val jsdocs = project
  .enablePlugins(ScalaJSBundlerPlugin)
  .dependsOn(shironekoSlinkyJS)
  .settings(commonSettings ++ noCrossBuild ++ noPublish)
  .settings(
    name := "shironeko-slinky-jsdocs",
    scalaJSUseMainModuleInitializer := true,
    npmDependencies in Compile ++= commonNpmDependencies,
    libraryDependencies ++= Seq(
      "me.shadaj" %%% "slinky-web" % "0.6.5",
    ),
    scalacOptions ++= { if (scalaJSVersion.startsWith("0.6.")) Seq("-P:scalajs:sjsDefinedByDefault") else Nil },
    webpackBundlingMode := BundlingMode.LibraryOnly(),
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) }
    //    scalaJSLinkerConfig ~= { _.withOptimizer(false) },
  )

lazy val todoMVC = project
  .in(file("todo-mvc"))
  .enablePlugins(ScalaJSBundlerPlugin, ScalablyTypedConverterPlugin)
  .dependsOn(shironekoSlinkyJS)
  .settings(commonSettings ++ noCrossBuild ++ noPublish)
  .settings(
    name := "shironeko-slinky-todomvc",
    stFlavour := Flavour.Slinky,
    npmDependencies in Compile ++= commonNpmDependencies ++ Seq(

      "file-loader" -> "6.0.0",
      "style-loader" -> "1.1.1",
      "css-loader" -> "3.5.2",
      "html-webpack-plugin" -> "4.2.0",
      "copy-webpack-plugin" -> "5.1.1",

      "react-router-dom" -> "5.1.2",
      "@types/react-router-dom" -> "5.1.2",
      "todomvc-app-css" -> "2.2.0"
    ),

    libraryDependencies ++= Seq(
      "me.shadaj" %%% "slinky-web" % "0.6.5",
      "me.shadaj" %%% "slinky-hot" % "0.6.5",
      "io.monix" %%% "monix-eval" % "3.2.2",
    ),
    scalacOptions ++= { if (scalaJSVersion.startsWith("0.6.")) Seq("-P:scalajs:sjsDefinedByDefault") else Nil },
    version in webpack                     := "4.42.1",
    version in startWebpackDevServer       := "3.10.3",
    webpackResources := baseDirectory.value / "webpack" * "*",
    webpackConfigFile in fastOptJS := Some(baseDirectory.value / "webpack" / "webpack-fastopt.config.js"),
    webpackConfigFile in fullOptJS := Some(baseDirectory.value / "webpack" / "webpack-opt.config.js"),
    webpackConfigFile in Test := Some(baseDirectory.value / "webpack" / "webpack-core.config.js"),
    webpackDevServerExtraArgs in fastOptJS := Seq("--inline", "--hot"),
    webpackBundlingMode in fastOptJS := BundlingMode.LibraryOnly(),

    addCommandAlias("dev", ";fastOptJS::startWebpackDevServer;~fastOptJS"),
    addCommandAlias("build", "fullOptJS::webpack"),
  )

def noPublish = List(
  skip in publish := true,
  publish := {},
  publishLocal := {},
  publishArtifact := false,
  publishTo := None,
)

def crossBuild = List(
  scalaVersion := scala213,
  crossScalaVersions := supportedScalaVersions,
)

def noCrossBuild = List(
  scalaVersion := scala213,
  crossScalaVersions := Seq(scala213),
)

def commonSettings = List(
  name := "shironeko",
  organization := "com.olegpy",
  version := "0.1.0-RC4",

  resolvers += Resolver.sonatypeRepo("snapshots"),
  licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
  homepage := Some(url("http://github.com/oleg-py/shironeko")),

  libraryDependencies ++= Seq(
    scalaOrganization.value % "scala-reflect" % scalaVersion.value % "provided",
    scalaOrganization.value % "scala-compiler" % scalaVersion.value % "provided",

    "org.typelevel" %%% "cats-effect" % "2.1.3",
    "co.fs2"        %%% "fs2-core"    % "2.3.0",
    compilerPlugin("org.typelevel" % "kind-projector" % "0.11.0" cross CrossVersion.full),
  ),
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, n)) if n >= 13 => Seq("-Ymacro-annotations")
      case _ => Nil
    }
  },
  libraryDependencies ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, v)) if v <= 12 =>
        Seq(compilerPlugin(("org.scalamacros" % "paradise" % "2.1.1").cross(CrossVersion.full)))
      case _ => Nil
    }
  },

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
