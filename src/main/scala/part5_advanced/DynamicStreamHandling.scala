package part5_advanced

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{BroadcastHub, Keep, MergeHub, Sink, Source}
import akka.stream.{ActorMaterializer, KillSwitches}

import scala.concurrent.duration._
import scala.language.postfixOps

object DynamicStreamHandling extends App {

  implicit val system: ActorSystem = ActorSystem("DynamicStreamHandling")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  // #1: Kill Switch

  val killSwitchFlow = KillSwitches.single[Int]
  val counter = Source(Stream.from(1)).throttle(1, 1 second).log("counter")
  val sink = Sink.ignore

  val killSwitch = counter
    .viaMat(killSwitchFlow)(Keep.right)
    .to(sink)
//    .run()

//  system.scheduler.scheduleOnce(3 seconds) {
//    killSwitch.shutdown()
//  }

  // shared kill switch
  val anotherCounter = Source(Stream.from(1)).throttle(2, 1 second).log("anotherCounter")
  val sharedKillSwitch = KillSwitches.shared("oneButtonToRuleThemAll")

//  counter.via(sharedKillSwitch.flow).runWith(Sink.ignore)
//  anotherCounter.via(sharedKillSwitch.flow).runWith(Sink.ignore)

//  system.scheduler.scheduleOnce(3 seconds) {
//    sharedKillSwitch.shutdown()
//  }

  // MergeHub

  // Merge requires a sink
  // dynamicMerge generates Int (it's a source), materialized to Sink[Int, NotUsed]
  val dynamicMerge: Source[Int, Sink[Int, NotUsed]] = MergeHub.source[Int]
  // Sink.foreach[Int](println) is the materialized sink
  val materializedSink: Sink[Int, NotUsed] = dynamicMerge.to(Sink.foreach[Int](println)).run()

  // use this sink any time we like
  Source(1 to 10).runWith(materializedSink)
  counter.runWith(materializedSink)

  // BroadcastHub

  val dynamicBroadcast: Sink[Int, Source[Int, NotUsed]] = BroadcastHub.sink[Int]
  // Source(1 to 100) will be the materialized source!
  val materializedSource = Source(1 to 100).runWith(dynamicBroadcast)

  materializedSource.runWith(Sink.ignore)
  materializedSource.runWith(Sink.foreach[Int](println))

  /** Challenge - combine a mergeHub and a broadcastHub.
    *
    * A publisher-subscriber component
    */
  val merge: Source[String, Sink[String, NotUsed]] = MergeHub.source[String]
  val bcast: Sink[String, Source[String, NotUsed]] = BroadcastHub.sink[String]
  val (publisherPort: Sink[String, NotUsed], subscriberPort: Source[String, NotUsed]) = merge.toMat(bcast)(Keep.both).run()

  subscriberPort.runWith(Sink.foreach(e => println(s"I received: $e")))
  subscriberPort
    .map(string => string.length)
    .runWith(Sink.foreach(n => println(s"I got a number: $n")))

  Source(List("Akka", "is", "amazing")).runWith(publisherPort)
  Source(List("I", "love", "Scala")).runWith(publisherPort)
  Source.single("STREEEEEEAMS").runWith(publisherPort)

}
