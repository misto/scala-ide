/*
 * Copyright 2011 LAMP/EPFL
 */

package scala.tools.eclipse
package codeanalysis

import java.util.regex.Pattern
import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IMarker
import org.eclipse.core.runtime.Platform
import scala.tools.eclipse.util.EclipseResource
import scala.tools.nsc.util.Position
import scala.util.control.Exception.catching

object CodeAnalysisExtensionPoint {

  val ParticipantsId = "org.scala-ide.sdt.codeanalysis.scalaCodeAnalysis"

  val MarkerType = "org.scala-ide.sdt.codeanalysis.scalaCodeAnalysisProblem"

  case class ExtensionPointDescription(id: String, name: String, markerId: String, severity: Int, msgPattern: String)

  lazy val extensions: List[ExtensionPointDescription] = {

    val configs = Platform.getExtensionRegistry.getConfigurationElementsFor(ParticipantsId).toList

    configs map { e =>

      val (markerType, severity) = e.getChildren.toList match {

        case child :: Nil =>

          val markerId = Option(child.getAttribute("id")) getOrElse MarkerType
          val severity = Option(child.getAttribute("severity")) flatMap {
              catching(classOf[NumberFormatException]) opt _.toInt
            } getOrElse IMarker.SEVERITY_WARNING

          (markerId, severity)

        case _ =>
          (MarkerType, IMarker.SEVERITY_WARNING)
      }

      val List(analyzerName, analyzerId, msgPattern) = List("name", "id", "msgPattern") map e.getAttribute

      ExtensionPointDescription(analyzerId, analyzerName, markerType, severity, msgPattern)
    }
  }

  private lazy val extensionsMessageRegexes: List[(java.util.regex.Pattern, ExtensionPointDescription)] = {
    extensions map { extension =>
      (Pattern.compile(extension.msgPattern.replaceAll("%s", ".*")), extension)
    }
  }

  def handleMessage(pos: Position, msg: String): Boolean = {

    if (!pos.isDefined) return false

    extensionsMessageRegexes.find(_._1.matcher(msg).matches) exists {
      case (_, ExtensionPointDescription(analyzerId, _, markerType, _, _)) =>
        pos.source.file match {
          case EclipseResource(file: IFile) =>
            if(CodeAnalysisPreferences.isEnabledForProject(file.getProject, analyzerId)) {
              val severity = CodeAnalysisPreferences.getSeverityForProject(file.getProject, analyzerId)
              addMarker(file, markerType, msg, pos.line, severity)
            }
        }
        true
      case _ => false
    }
  }

  private def addMarker(file: IFile, markerType: String, message: String, lineNumber: Int, severity: Int) {
    val marker = file.createMarker(markerType)
    marker.setAttribute(IMarker.MESSAGE, message)
    marker.setAttribute(IMarker.SEVERITY, severity)
    marker.setAttribute(IMarker.LINE_NUMBER, if (lineNumber == -1) 1 else lineNumber)
  }
}
