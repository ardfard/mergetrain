package net.ardfard

import zio._

package object mergetrain {

  import queue._
  import ci._

  trait MergeTrain {
    def initialize(): Task[WorldView]
    def update(zawarudo: WorldView): Task[WorldView]
    def act(zawarudo: WorldView): Task[WorldView]
  }

  case class Config(maxRunning: Int)
  type Configuration = Has[Configuration.Service]

  final case class WorldView(
      val runningPipelines: Seq[(Pipeline, PullRequest)]
  ) {
    def isSkipAct: Boolean = {
      def loop(
          runningExist: Boolean,
          rp: Seq[(Pipeline, PullRequest)]
      ): Boolean =
        (runningExist, rp) match {
          case (_, Nil) => false
          case (true, (p, _) :: rest) =>
            p.state match {
              case Pipeline.Completed(Pipeline.Failed) => true
              case _                                   => loop(true, rest)
            }
          case (false, (p, _) :: rest) =>
            p.state match {
              case Pipeline.Completed(Pipeline.Failed) => false
              case Pipeline.Running                    => loop(true, rest)
              case _                                   => loop(false, rest)
            }
          case (p, rp) => loop(p, rp.tail)
        }
      loop(false, runningPipelines)
    }
  }

  type RepoOperation = Has[RepoOperation.Service]

  def initialize(): RIO[Queue with CI with Configuration, WorldView] =
    for {
      maxRunning <- Configuration.config.map(_.maxRunning)
      activePipeline <- ZIO.foreachPar(0.to(maxRunning - 1)) { idx =>
        for {
          pr <- Queue.getAt(idx)
          if (pr.isDefined)
          pipeline <- CI.getPipeline(pr.get.id)
          if (pipeline.isDefined)
        } yield ((pipeline.get, pr.get))
      }
    } yield (WorldView(activePipeline))

  def update(
      zawarudo: WorldView
  ): RIO[CI, WorldView] =
    for {
      updated <- RIO.foreachPar(zawarudo.runningPipelines) {
        case (p, pr) =>
          for {
            newP <- CI.getPipeline(p.id)
            if (newP.isDefined)
          } yield (newP.get, pr)
      }
    } yield (zawarudo.copy(
      runningPipelines = updated
    ))

  def act(
      zawarudo: WorldView
  ): RIO[
    CI with Queue with RepoOperation with Configuration,
    WorldView
  ] = {

    def processPipelines(
        current: Seq[(Pipeline, PullRequest)],
        running: Seq[(Pipeline, PullRequest)]
    ): RIO[CI with Queue with RepoOperation, Seq[(Pipeline, PullRequest)]] =
      current match {
        case Nil => ZIO.succeed(running)
        case (p, pr) :: next =>
          p.state match {
            case Pipeline.Completed(Pipeline.Success) => {
              val cancelRunning =
                ZIO.foreachPar(running.map(_._1))(p => CI.cancelPipeline(p.id))
              val mergeBranch = ZIO.foreach(running.map(_._2) :+ pr) { _pr =>
                Queue.remove(_pr.id) *> RepoOperation.mergeBranch(
                  "master",
                  _pr.branch
                )
              }
              ZIO.collectAllPar(
                Seq(cancelRunning, mergeBranch)
              ) *> processPipelines(
                next,
                Nil
              )
            }
            case Pipeline.Completed(Pipeline.Cancelled) => ZIO.succeed(running)
            case Pipeline.Completed(Pipeline.Failed) => {
              val cancelPipelines = ZIO.foreachPar(next) {
                case (_p, _pr) => CI.cancelPipeline(_p.id)
              }

              val runNewPipelines = ZIO.foldLeft(next.map(_._2))(
                (Seq.empty[(Pipeline, PullRequest)], "master")
              ) {
                case ((acc, b), _pr) =>
                  for {
                    b <- RepoOperation.createStagingBranch(_pr.branch, b)
                    pipeline <- CI.createPipeline(b)
                  } yield ((acc :+ (pipeline, _pr), b))
              }
              val removeFromQueue = Queue.remove(pr.id)

              removeFromQueue &> cancelPipelines &> runNewPipelines >>= {
                case (running, _) => ZIO.succeed(running)
              }
            }
            case Pipeline.Running => processPipelines(next, running :+ (p, pr))
            case _                => processPipelines(next, running)
          }
      }

    for {
      ps <- processPipelines(zawarudo.runningPipelines, Nil)
      maxRunning <- Configuration.config.map(_.maxRunning)
      lastBranchRef <- Ref.make(ps.last._1.ref)
      prsToRun <-
        ZIO.collectAllSuccesses(ps.size.to(maxRunning - 1).map { idx =>
          Queue.getAt(idx).someOrFailException
        })
      newPipelines <- ZIO.foreach(prsToRun) { pr =>
        for {
          lastBranch <- lastBranchRef.get
          b <- RepoOperation.createStagingBranch(pr.branch, lastBranch)
          p <- CI.createPipeline(b)
          _ <- lastBranchRef.set(p.ref)
        } yield (p, pr)
      }
    } yield (zawarudo.copy(runningPipelines = ps ++ newPipelines))
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

  def getPullRequests(): RIO[Queue, Seq[(PullRequest, Priority)]] =
    Queue.getAll()

}
