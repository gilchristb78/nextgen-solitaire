package de.tu_dortmund.cs.ls14.git

import java.nio.file._

import shapeless.feat.Enumeration
import de.tu_dortmund.cs.ls14.cls.inhabitation.Tree
import de.tu_dortmund.cs.ls14.cls.interpreter._
import de.tu_dortmund.cs.ls14.cls.types.Type
import de.tu_dortmund.cs.ls14.{Persistable, html}
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand.ResetType
import org.eclipse.jgit.revwalk.RevCommit
import org.webjars.play.WebJarsUtil
import play.api.mvc._

abstract class InhabitationController(webJars: WebJarsUtil) extends InjectedController {
  private lazy val root = Files.createTempDirectory("inhabitants")
  private lazy val git = Git.init().setDirectory(root.toFile()).call()
  private lazy val computedVariations = collection.mutable.Set.empty[Long]

  def init() : Unit = {
    root.toFile.deleteOnExit()
  }
  init()

  val combinatorComponents: Map[String, CombinatorInfo]

  val sourceDirectory = Paths.get(".")

  sealed trait InhabitationResultVector[R] {
    def add(newResults: R, oldResults: Results): Results
  }

  sealed trait InhabitationResultVectorInstances {
    implicit def persistable[R](implicit persist: Persistable.Aux[R]): InhabitationResultVector[InhabitationResult[R]] =
      new InhabitationResultVector[InhabitationResult[R]] {
        def add(newResults: InhabitationResult[R], oldResults: Results): Results =
          oldResults.add[R](newResults)(persist)
      }

    implicit def product[L, R]
      (implicit persist: Persistable.Aux[R],
        vector: InhabitationResultVector[L]) =
      new InhabitationResultVector[(L, InhabitationResult[R])] {
        def add(newResults: (L, InhabitationResult[R]), oldResults: Results): Results =
          vector.add(newResults._1, oldResults).add[R](newResults._2)(persist)
      }
  }

  object InhabitationResultVector extends InhabitationResultVectorInstances {
    def apply[R](implicit vectorInst: InhabitationResultVector[R]): InhabitationResultVector[R] =
      vectorInst
  }

  sealed trait Results { self =>
    val targets: Seq[(Type, Option[BigInt])]
    val raw: Enumeration[Seq[Tree]]
    val infinite: Boolean
    val incomplete: Boolean
    val persistenceActions: Enumeration[Seq[() => Unit]]
    def storeToDisk(fileSystemRoot: Path, number: Long): Unit = {
      val result = persistenceActions.index(number)
      result.foreach(_.apply())
    }


    def add[R](inhabitationResult: InhabitationResult[R], repositoryPath: Path): Results =
      add[R](inhabitationResult)(new Persistable {
        type T = R
        override def rawText(elem: T) = elem.toString
        override def path(elem: T) = repositoryPath
      })

    def add[T](inhabitationResult: InhabitationResult[T])(implicit persistable: Persistable.Aux[T]): Results = {
      val size = inhabitationResult.size

      size match {
        case Some(x) if x == 0 => new Results {
            val targets = self.targets :+ (inhabitationResult.target, size)
            val raw = self.raw
            val persistenceActions = self.persistenceActions
            val infinite = self.infinite
            val incomplete = true
          }
        case  _ => new Results {
          val targets = self.targets :+ (inhabitationResult.target, inhabitationResult.size)
          val raw = self.raw.product(inhabitationResult.terms).map {
            case (others, next) => others :+ next
          }
          val persistenceActions = self.persistenceActions.product(inhabitationResult.interpretedTerms).map {
            case (ps, r) => ps :+ (() => persistable.persist(root.resolve(sourceDirectory), r))
          }
          val infinite = self.infinite || inhabitationResult.isInfinite
          val incomplete = self.incomplete
        }
      }
    }

    def addAll[R](results: R)(implicit canAddAll: InhabitationResultVector[R]): Results =
      canAddAll.add(results, this)
  }
  object Results extends Results {
    val targets = Seq.empty
    val raw = Enumeration.singleton(Seq())
    val persistenceActions = Enumeration.singleton(Seq())
    val infinite = false
    val incomplete = false
  }

  val results: Results

  private def checkoutEmptyBranch(id: Long): Unit = {
    git
      .checkout()
      .setOrphan(true)
      .setName(s"variation_$id")
      .call()
    git.reset()
      .setMode(ResetType.HARD)
      .call()
  }

  private def addAllFilesToCurrentBranch(): RevCommit = {
    git
      .add()
      .addFilepattern(".")
      .call()
    git
      .commit()
      .setMessage("Next variation")
      .call()
  }

  private def updateInfo(rev: RevCommit, id: Long): Unit = {
    val info = Paths.get(root.toString, ".git", "info")
    Files.createDirectories(info)
    val refs = Paths.get(root.toString, ".git", "info", "refs")
    val line = s"${rev.getName}\trefs/heads/variation_${id}\n"
    Files.write(refs, line.getBytes, StandardOpenOption.APPEND, StandardOpenOption.CREATE)
  }

  def prepare(number: Long) = Action {
    val branchFile = Paths.get(root.toString, ".git", "refs", "heads", s"variation_${number}")
    val result = results.raw.index(number).toString
    if (!Files.exists(branchFile)) {
      checkoutEmptyBranch(number)
      results.storeToDisk(root, number)
      val rev = addAllFilesToCurrentBranch()
      updateInfo(rev, number)
      computedVariations.add(number)
    }
    Ok(result)
    //Redirect(".")
  }

  def overview() = Action { request =>
    val combinators = combinatorComponents.mapValues {
      case staticInfo: StaticCombinatorInfo =>
        (ReflectedRepository.fullTypeOf(staticInfo),
          s"${scala.reflect.runtime.universe.show(staticInfo.fullSignature)}")
      case dynamicInfo: DynamicCombinatorInfo[_] =>
        (ReflectedRepository.fullTypeOf(dynamicInfo),
          dynamicInfo.position.mkString("\n"))
    }
    Ok(html.overview.render(request.path, webJars, combinators, results.targets, results.raw, computedVariations.toSet, results.infinite, results.incomplete))
  }
  def raw(id: Long) = {
    TODO
  }

  def serveFile(name: String) = Action {
    try {
      Ok(Files.readAllBytes(root.resolve(Paths.get(".git", name))))
    } catch {
      case _: NoSuchFileException => play.api.mvc.Results.NotFound(s"404, File not found: $name")
      case _: AccessDeniedException => play.api.mvc.Results.Forbidden(s"403, Forbidden: $name")
    }
  }
}