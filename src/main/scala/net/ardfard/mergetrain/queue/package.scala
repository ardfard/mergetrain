package net.ardfard.mergetrain
import zio._
import zio.macros.accessible
import scala.collection.mutable.ArrayBuffer
import com.redis._
import com.redis.serialization.{Format, Parse}
import java.security.Provider.Service
import zio.blocking._
import zio.clock._
import java.io.{
  ObjectOutputStream,
  ObjectInputStream,
  ByteArrayInputStream,
  ByteArrayOutputStream
}
import com.redis.serialization.Parse

package object queue {
  type Queue = Has[Queue.Service]
  type Priority = Int

  @accessible
  object Queue {
    trait Service {
      def push(pr: PullRequest, priority: Priority): Task[Unit]
      def pop(): Task[PullRequest]
      def remove(pr: PullRequest): Task[Unit]
      def getAll(): Task[Seq[PullRequest]]
      def getAt(pos: Int): Task[PullRequest]
    }

    val redisClient: Layer[Throwable, Has[RedisClient]] =
      ZLayer.fromAcquireRelease(ZIO.effect(new RedisClient("localhost", 6379)))(
        r => UIO(r.close())
      )

    private val key = "mergetrain:"

    private def serialize(pr: PullRequest): Task[Array[Byte]] =
      ZIO.effect {
        val byteOut = new ByteArrayOutputStream()
        val objOut = new ObjectOutputStream(byteOut)
        objOut.writeObject(pr)
        objOut.close()
        byteOut.toByteArray()
      }

    private def deserialize(bytes: Array[Byte]): Task[PullRequest] =
      ZIO.effect {
        val byteIn = new ByteArrayInputStream(bytes)
        val objIn = new ObjectInputStream(byteIn)
        val obj = objIn.readObject().asInstanceOf[PullRequest]
        byteIn.close()
        obj
      }

    private def rankWithPriority(nanotime: Long, priority: Int): Task[Long] =
      Task.effect(s"$priority$nanotime".toLong)

    val redisLayer
        : RLayer[Has[RedisClient] with Blocking with clock.Clock, Queue] =
      ZLayer.fromServices[
        RedisClient,
        Blocking.Service,
        clock.Clock.Service,
        Service
      ]((r, blocking, clock) =>
        new Service {
          def push(pr: PullRequest, priority: Priority): zio.Task[Unit] =
            for {
              serialized <- serialize(pr)
              nanotime <- clock.nanoTime
              rank <- rankWithPriority(nanotime, priority)
              _ <-
                blocking
                  .effectBlocking(
                    r.zadd(key, rank, serialized)
                  )
                  .someOrFail(new Throwable())
            } yield ()
          def getAll(): zio.Task[Seq[PullRequest]] =
            blocking
              .effectBlocking(
                r.zrange(key, 0, -1)(
                  Format.default,
                  Parse.Implicits.parseByteArray
                )
              )
              .someOrFailException
              .flatMap(result =>
                ZIO.foreach(result) { b =>
                  for {
                    pr <- deserialize(b)
                  } yield pr
                }
              )

          def getAt(pos: Int): zio.Task[PullRequest] =
            blocking
              .effectBlocking(
                r.zrange(key, pos, pos + 1)(
                  Format.default,
                  Parse.Implicits.parseByteArray
                )
              )
              .someOrFailException
              .flatMap(res => ZIO.effect(res.head))
              .flatMap(res => deserialize(res))

          def remove(pr: PullRequest): zio.Task[Unit] =
            serialize(pr).flatMap(serialized =>
              blocking.effectBlocking(r.zrem(key, serialized))
            )
          def pop(): zio.Task[PullRequest] =
            for {
              pr <- getAt(0)
              _ <- remove(pr)
            } yield pr
        }
      )
    val live = (ZLayer.identity[Blocking] ++ ZLayer
      .identity[Clock] ++ redisClient) >>> redisLayer
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
      def getAll(): zio.Task[Seq[PullRequest]] = {
        ZIO.succeed(queue.toList map (_._1))
      }

      def pop(): zio.Task[PullRequest] = {
        Task.succeed(queue.remove(0)._1)
      }
      def getAt(pos: Int): zio.Task[PullRequest] =
        console.get.putStrLn(s"get at $pos: $queue") *>
          Task.effect(queue(pos)._1)

      def remove(pr: PullRequest): zio.Task[Unit] = {
        val prIdx = queue
          .indexWhere(_._1.id == pr.id)
        console.get.putStrLn(s"removed ${pr.id}") *> (if (prIdx == -1)
                                                        ZIO.fail(new Throwable)
                                                      else
                                                        ZIO.effectTotal {
                                                          val pr =
                                                            queue(prIdx)._1
                                                          queue.remove(prIdx)
                                                        })
      }
    }

  }
}
