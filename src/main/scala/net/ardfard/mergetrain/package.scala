package net.ardfard

import zio._
import zio.stream.ZStream.Pull
import net.ardfard.mergetrain.net.ardfard.mergetrain.PRValidation
import net.ardfard.mergetrain.WorldView
import java.nio.channels.Pipe

package object mergetrain {

  import queue._
  import ci._

  trait MergeTrain {
    def initialize(): Task[WorldView]
    def update(zawarudo: WorldView): Task[WorldView]
    def act(zawarudo: WorldView): Task[WorldView]
  }

  final case class WorldView(
      val runningPipelines: Seq[(Pipeline, PullRequest)],
      val completed: Seq[(Pipeline, PullRequest)],
      val latestBranch: String,
      val used: Int
  )

  type RepoOperation = Has[RepoOperation.Service]

  def update(
      zawarudo: WorldView
  ): RIO[CI, WorldView] =
    for {
      updated <- RIO.foreachPar(zawarudo.runningPipelines) {
        case (p, pr) =>
          for {
            newP <- CI.getPipeline(p.id)
          } yield (newP, pr)
      }
      val (completed, running) = updated.partition {
        case (p, pr) =>
          p.state match {
            case Pipeline.Completed(result) => true
            case _                          => false
          }
      }
    } yield (zawarudo.copy(
      runningPipelines = updated,
      completed = completed
    ))

  def act(
      zawarudo: WorldView
  ): RIO[CI with Queue with RepoOperation, WorldView] = {
    def processPipelines(
        xs: Seq[(Pipeline, PullRequest)],
        running: Seq[(Pipeline, PullRequest)]
    ): RIO[CI with Queue with RepoOperation, Seq[(Pipeline, PullRequest)]] = ???
    // xs match {
    //   case Nil => ZIO.succeed(running)
    //   case (p, pr) :: next =>
    //     p.state match {
    //       case Pipeline.Completed(Pipeline.Success) =>
    //         (for {
    //           _ <- ZIO.foreachPar(running.map(_._1))(p =>
    //             CI.cancelPipeline(p.id)
    //           )
    //           _ <- ZIO.foreach(running.map(_._2) :+ pr)(_pr =>
    //             Queue.remove(_pr.id) *> RepoOperation
    //               .mergeBranch("master", _pr.branch)
    //           )
    //         } yield ()) *> processPipelines(next, Nil)
    //       case Pipeline.Completed(Pipeline.Cancelled) => ???
    //       case Pipeline.Completed(Pipeline.Failed) =>
    //         if (running.isEmpty) {
    //           ZIO.foreachPar(next) {
    //             case (_p, _pr) => CI.cancelPipeline(_p.id)
    //           } &> ZIO.foldLeft(next.map(_._2))(
    //             (List.empty[Pipeline], "master")
    //           ) {
    //             case ((acc, b), _pr) =>
    //               for {
    //                 b <- RepoOperation.createBranch(pr.branch)
    //                 pipeline <- CI.createPipeline(b)
    //               } yield (pipeline)
    //           }
    //         } else ZIO.succeed(running.appended((p, pr)).appendedAll(next))
    //       case _ => ???
    //     }

    // }

    processPipelines(zawarudo.runningPipelines, List.empty).flatMap(ps =>
      ZIO.succeed(zawarudo.copy(runningPipelines = ps))
    )

    /* >>= (p =>
      ZIO.foreach(p.size.to(5)) { i => Queue.get(pos) }*/

  }

  type PRValidation = Has[PRValidation.Service]
  def queuePullRequest(
      pr: PullRequest,
      priority: Int
  ): RIO[Queue with PRValidation, Unit] = {
    PRValidation.validate(pr) *> Queue.push(pr, priority)
  }

  def unqueuePullRequest(
      pr: PullRequest
  ): RIO[Queue, Unit] =
    for {
      _ <- Queue.remove(pr.id)
    } yield ()

  def getPullRequests(): RIO[Queue, List[(PullRequest, Priority)]] =
    Queue.getAll()

  def insertPullRequestAtPos(
      pr: PullRequest,
      pos: Int
  ): RIO[Queue with PRValidation, Unit] =
    PRValidation.validate(pr) *> Queue.insert(pr, pos)

  // def processQueue(
  //     maxRunningPipeline: Int
  // ): RIO[Queue with CI with WorldView, Unit] =
  //   for {
  //     pullRequests <- getPullRequests()
  //     _ <- ZIO.accessM[WorldView](w =>
  //       for {
  //         pipelines <- w.runningPipeline.get
  //         val running = pipelines.filter(_._2.state == Pipeline.Running)
  //         current <- Ref.make(running.size)
  //       } yield ()
  //     )
  //   } yield ()
}
