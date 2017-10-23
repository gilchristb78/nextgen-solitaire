package org.combinators.solitaire.idiot

import javax.inject.Inject

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.expr.SimpleName
import com.github.javaparser.ast.expr.Name
import de.tu_dortmund.cs.ls14.cls.interpreter.ReflectedRepository
import de.tu_dortmund.cs.ls14.cls.types.syntax._
import de.tu_dortmund.cs.ls14.git.InhabitationController
import de.tu_dortmund.cs.ls14.java.JavaPersistable._
import domain.idiot.Domain
import org.combinators.solitaire.shared.SemanticTypes
import org.webjars.play.WebJarsUtil
// domain
import domain._


class Idiot @Inject()(webJars: WebJarsUtil) extends InhabitationController(webJars) {

  val s:Solitaire = new Domain()

  // semantic types are embedded/defined within the repository, so we need to
  // import them all for use.
  lazy val repository = new gameDomain(s) with controllers {}
  import repository._
  lazy val Gamma:ReflectedRepository[gameDomain] = repository.init(ReflectedRepository(repository, classLoader = this.getClass.getClassLoader), s)

  val inhabitants = Gamma.inhabit[Name](packageName).interpretedTerms
  print ("PKG-22:" + inhabitants.index(0))


  lazy val combinatorComponents = Gamma.combinatorComponents
  lazy val jobs =
    Gamma.InhabitationBatchJob[CompilationUnit](game(complete :&: game.solvable))
      .addJob[CompilationUnit](constraints(complete))
      .addJob[CompilationUnit](controller(deck, complete))
      .addJob[CompilationUnit](controller(column, complete))
      .addJob[CompilationUnit](move('RemoveCard :&: move.generic, complete))
      .addJob[CompilationUnit](move('MoveCard :&: move.generic, complete))
      .addJob[CompilationUnit](move('DealDeck :&: move.generic, complete))
//
//      .addJob[CompilationUnit](move('RemoveCard :&: move.potential, complete))
//      .addJob[CompilationUnit](move('MoveCard :&: move.potential, complete))
//      .addJob[CompilationUnit](move('DealDeck :&: move.potential, complete))

  lazy val results:Results = Results.addAll(jobs.run())

}
