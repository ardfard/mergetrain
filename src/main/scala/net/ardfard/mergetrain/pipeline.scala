package net.ardfard.mergetrain

final case class Pipeline(id: String, ref: String, state: Pipeline.State)

object Pipeline {

  sealed trait Result
  object Success extends Result
  object Failed extends Result
  object Cancelled extends Result

  sealed trait State
  object Pending extends State
  object Running extends State
  case class Completed(result: Result) extends State
}
