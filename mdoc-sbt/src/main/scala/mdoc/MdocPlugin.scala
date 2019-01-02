package mdoc

import sbt.Def
import sbt.Keys._
import sbt._
import scala.collection.mutable.ListBuffer

object Main {
  import MdocPlugin.autoImport._
  def main(args: Array[String]): Unit = {
    println(mdocIn)
    println(mdocIn.key)
    println(mdocIn.key.description)
    println(mdocIn.key.manifest)
    println(mdocIn.key.manifest.runtimeClass.getName)
  }
}

object MdocPlugin extends AutoPlugin {
  case class CompileOptions(options: Seq[String], classpath: Seq[File])
  object autoImport {
    val mdoc =
      inputKey[Unit](
        "Run mdoc to generate markdown sources. " +
          "Supports arguments like --watch to start the file watcher with livereload."
      )
    val mdocVariables =
      settingKey[Map[String, String]](
        "Site variables that can be referenced from markdown with @VERSION@."
      )
    val mdocIn =
      settingKey[File](
        "Input directory containing markdown sources to be processed by mdoc. " +
          "Defaults to the toplevel docs/ directory."
      )
    val mdocOut =
      settingKey[File](
        "Output directory for mdoc generated markdown. " +
          "Defaults to the target/mdoc directory of this project."
      )
    val mdocExtraArguments =
      settingKey[Seq[String]](
        "Additional command-line arguments to pass on every mdoc invocation. " +
          "For example, add --no-link-hygiene to disable link hygiene."
      )
    val mdocJS =
      taskKey[Option[CompileOptions]](
        "Optional Scala.js classpath and compiler options to use for the mdoc:js modifier. " +
          "To use this setting, set the value to `mdocJS := Some(mdocCompileOptions(jsproject).value)` where `jsproject` must be a Scala.js project."
      )
    val mdocAutoDependency =
      settingKey[Boolean](
        "If false, do not add mdoc as a library dependency this project. " +
          "Default value is true."
      )
    def mdocCompileOptions(ref: Project): Def.Initialize[Task[CompileOptions]] =
      Def.task {
        CompileOptions(
          scalacOptions.in(ref, Compile).value,
          fullClasspath.in(ref, Compile).value.map(_.data)
        )
      }
  }
  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] = List(
    mdocIn := baseDirectory.in(ThisBuild).value / "docs",
    mdocOut := target.in(Compile).value / "mdoc",
    mdocVariables := Map.empty,
    mdocAutoDependency := true,
    mdocExtraArguments := Nil,
    mdoc := Def.inputTaskDyn {
      val parsed = sbt.complete.DefaultParsers.spaceDelimited("<arg>").parsed
      val args = mdocExtraArguments.value ++ parsed
      Def.taskDyn {
        runMain.in(Compile).toTask(s" mdoc.Main ${args.mkString(" ")}")
      }
    }.evaluated,
    libraryDependencies ++= {
      if (mdocAutoDependency.value) {
        List("org.scalameta" %% "mdoc" % BuildInfo.version)
      } else {
        List()
      }
    },
    resourceGenerators.in(Compile) += Def.task {
      val out =
        managedResourceDirectories.in(Compile).value.head / "mdoc.properties"
      val props = new java.util.Properties()
      mdocVariables.value.foreach {
        case (key, value) =>
          props.put(key, value)
      }
      mdocJS.value.foreach { options =>
        props.put(
          s"js.scalacOptions",
          options.options.mkString(" ")
        )
        props.put(
          s"js.classpath",
          options.classpath.mkString(java.io.File.pathSeparator)
        )
      }
      props.put("in", mdocIn.value.toString)
      props.put("out", mdocOut.value.toString)
      props.put(
        "scalacOptions",
        scalacOptions.in(Compile).value.mkString(" ")
      )
      val classpath = ListBuffer.empty[File]
      classpath ++= dependencyClasspath.in(Compile).value.iterator.map(_.data)
      classpath += classDirectory.in(Compile).value
      props.put(
        "classpath",
        classpath.mkString(java.io.File.pathSeparator)
      )
      IO.write(props, "mdoc properties", out)
      List(out)
    }
  )

}
