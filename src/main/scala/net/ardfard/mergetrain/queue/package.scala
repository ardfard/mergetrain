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
      def getAt(pos: Int): Task[Option[PullRequest]]
    }
  }

  import console._
  val inMemory: RLayer[Console, Queue] = ZLayer.fromFunction { console =>
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
      def getAt(pos: Int): zio.Task[Option[PullRequest]] = {
        console.get.putStrLn(s"get at $pos: $queue") *>
          Task.succeed(
            if (queue.isDefinedAt(pos)) Some(queue(pos)._1) else None
          )
      }
      def remove(id: String): zio.Task[PullRequest] = {
        val prIdx = queue
          .indexWhere(_._1.id == id)
        console.get.putStrLn(s"removed $id") *> (if (prIdx == -1)
                                                   ZIO.fail(new Throwable)
                                                 else
                                                   ZIO.effectTotal {
                                                     val pr = queue(prIdx)._1
                                                     queue.remove(prIdx)
                                                     pr
                                                   })
      }
    }

  }
}
