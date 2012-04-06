package scala.tools.eclipse.codeanalysis

import org.eclipse.core.resources.IProject
import org.eclipse.core.runtime.{Platform, FileLocator}

import scala.Option.option2Iterable
import scala.tools.eclipse.buildmanager.CompilerSettingsExtension
import scala.tools.nsc.Settings
import scala.tools.eclipse.logging.HasLogger

/**
 * An extension point that adds the refactoring and codeanalysis
 * jars as plug-ins to the compiler.
 */
class CompilerPluginSettings extends CompilerSettingsExtension with HasLogger {

  def modifySettings(project: IProject, settings: tools.nsc.Settings) {

    val setPlugin = settings.plugin.appendToValue _

    if(CodeAnalysisPreferences.isGenerallyEnabledForProject(project)) {

      getJarLocationForBundle("org.scala-refactoring.library") match {
        case Some(file) if file.endsWith("jar") =>
          setPlugin(file)
        case Some(dir) =>
          logger.error("Refactoring library is not a jar! To run in development mode, " +
          		"adapt the launch config to use an installed jar version.")
        case _ =>
          logger.error("Could not find scala-refactoring jar")
          return
      }

      getJarLocationForBundle("org.scala-ide.sdt.codeanalysis") match {
        case Some(file) if file.endsWith("jar") =>
          setPlugin(file)
        case Some(dir) =>
          /* If the codeanalysis plug-in is not a jar, then we are running in a 
           * secondary Eclipse instance launched during development. In that
           * case, we need to trick the Scala compiler by adding a minimal
           * compiler plug-in that then loads the code analyzers from the
           * development workspace.
           * */
          val jarName = "code-analysis-development-plugin.jar"
          logger.info("Development mode detected, adding "+ jarName +" to compiler")
          setPlugin(dir +"/target/"+ jarName)
        case None =>
          logger.error("Could not find codeanalysis jar")
      }
    }
  }

  private def getJarLocationForBundle(bundleName: String): Option[String] = {
    Option(Platform.getBundle(bundleName))
      .map(FileLocator.getBundleFile(_).getAbsolutePath)
  }
}
