package net.ardfard.mergetrain
import zio._
import zio.macros.accessible
import scala.collection.mutable.ArrayBuffer

package object queue {
  type Queue = Has[Queue.Service]
  type Priority = Int

  @accessible
  object Queue {
    trait Service {
      def push(pr: PullRequest, priority: Priority): Task[Unit]
      def pop(): Task[PullRequest]
      def remove(id: String): Task[PullRequest]
      def getAll(): Task[Seq[(PullRequest, Priority)]]
      def getAt(pos: Int): Task[PullRequest]
    }
  }

  val inMemory: ULayer[Queue] = ZLayer.fromFunction { _ =>
    val queue = new ArrayBuffer[(PullRequest, Priority)](10)
    new Queue.Service {
      def push(pr: PullRequest, priority: Priority): Task[Unit] = {
        ZIO.effect(
          queue.addOne((pr, priority)).sortInPlaceBy(_._2)
        )
      }
      def getAll(): zio.Task[Seq[(PullRequest, Priority)]] = {
        ZIO.succeed(queue.toList)
      }

      def pop(): zio.Task[PullRequest] = {
        Task.succeed(queue.remove(0)._1)
      }
      def getAt(pos: Int): zio.Task[PullRequest] = {
        Task.succeed(queue(pos)._1)
      }
      def remove(id: String): zio.Task[PullRequest] = {
        Task.fromEither(
          queue
            .find(_._1.id == id)
            .fold(Left(new Throwable): Either[Throwable, PullRequest])(p =>
              Right(p._1)
            )
        )
      }
    }

  }
}
