package net.ardfard.mergetrain
import zio._
import zio.macros.accessible

package object queue {
  type Queue = Has[Queue.Service]
  type Priority = Int

  @accessible
  object Queue {
    trait Service {
      def push(pr: PullRequest, priority: Priority): Task[Unit]
      def pop(): Task[PullRequest]
      def remove(id: String): Task[PullRequest]
      def insert(pr: PullRequest, pos: Int): Task[Unit]
      def getAll(): Task[List[(PullRequest, Priority)]]
    }
  }
}
