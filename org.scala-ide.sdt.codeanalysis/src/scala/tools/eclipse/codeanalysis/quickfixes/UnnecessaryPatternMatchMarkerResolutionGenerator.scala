/*
 * Copyright 2011 LAMP/EPFL
 */

package scala.tools.eclipse
package codeanalysis.quickfixes

import org.eclipse.core.resources.{IMarker, IFile}
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.internal.ui.JavaPluginImages
import org.eclipse.ui.{IMarkerResolutionGenerator, IMarkerResolution2}

import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.refactoring.EditorHelpers
import scala.tools.refactoring.common.InteractiveScalaCompiler
import scala.tools.refactoring.implementations.EliminateMatch

class UnnecessaryPatternMatchMarkerResolutionGenerator extends IMarkerResolutionGenerator {

  def getResolutions(marker: IMarker) = {

    val resolution = new AbstractMarkerResolution(marker) {
      def run(marker: IMarker) {
        val scalaSourceFile = ScalaSourceFile.createFromPath(marker.getResource.getFullPath.toString)

        scalaSourceFile foreach { scalaSourceFile =>

          val changes = scalaSourceFile.withSourceFile { (sourceFile, compiler) =>

            val r = new EliminateMatch with InteractiveScalaCompiler { val global = compiler }

            val selection = MarkerUtil.getLineNumberFromMarker(marker) match {
              case Some(line) =>
                val start = sourceFile.lineToOffset(line)
                val end = sourceFile.lineToOffset(line + 1) - 1 /*without any kind of newline*/
                new r.FileSelection(sourceFile.file, r.global.body(sourceFile), start, end)
              case _ => throw new Exception("Could not get line number from marker, please file a bug report")
            }

            r.prepare(selection).right map (r.perform(selection, _, new r.RefactoringParameters)) match {
              case Left(r.PreparationError(cause)) =>
                throw new Exception("Could not apply quickfix: "+ cause)
              case Right(Left(r.RefactoringError(cause))) =>
                throw new Exception("Could not apply quickfix: "+ cause)
              case Right((Right(changes))) => changes
            }

          }(Nil)

          marker.getResource match {
            case file: IFile =>
              EditorHelpers.createTextFileChange(file, changes).perform(new NullProgressMonitor)
            case _ =>
              throw new Exception("Marker's resource is not an IFile.")
          }
        }
      }
    }

    Array(resolution)
  }
}
