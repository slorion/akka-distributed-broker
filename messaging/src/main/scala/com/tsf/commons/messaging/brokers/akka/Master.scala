package com.tsf.commons.messaging.brokers.akka

import java.util.concurrent.ConcurrentHashMap

import akka.actor.{Actor, ActorLogging, ActorRef, Deploy, Props}
import akka.pattern.{AskTimeoutException, _}
import akka.routing.FromConfig
import akka.util.Timeout
import com.tsf.commons.net.http.HttpStatus
import com.typesafe.config.ConfigValueFactory

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

final class Master(broker: Broker) extends Actor with ActorLogging {
	private[this] val _defaultWorkRouter = context.actorOf(FromConfig.props(Props(classOf[Worker], broker)), name = "default-work-router")
	private[this] val _workRouterByRoles = new ConcurrentHashMap[Set[String], ActorRef]

	override def receive: Receive = {
		case request: RequestEnvelope[_] =>
			val routingInfo =
				broker.requestRouters.map(_._2).find(_.isDefinedAt(request)) match {
					case None => RoutingInformation.default
					case Some(f) => f.apply(request)
				}

			val workRouter =
				routingInfo match {
					case RoutingInformation.default =>
						_defaultWorkRouter

					case RoutingInformation(roles) if roles.isEmpty || (roles.size == 1 && roles.head == broker.settings.workerRoleName) =>
						_defaultWorkRouter

					case RoutingInformation(roles) =>
						_workRouterByRoles.computeIfAbsent(
							roles,
							_ => {
								val workRouterName = roles.toList.sorted.mkString("-") + "-router"

								if (broker.settings.config.hasPath(s"akka.actor.deployment./master/$workRouterName")) {
									context.actorOf(FromConfig.props(Props(classOf[Worker], broker)), name = workRouterName)
								} else {
									val conf = broker.settings.config.atPath("akka.actor.deployment./master/default-work-router")
										.withValue(s"akka.actor.deployment./master/$workRouterName.cluster.use-roles", ConfigValueFactory.fromIterable((roles ++ broker.settings.workerRoleName).asJava))

									context.actorOf(Props(classOf[Worker], broker).withDeploy(Deploy(config = conf)), name = workRouterName)
								}
							})
				}

			val replyTo = sender()

			implicit val timeout = Timeout(broker.settings.messageTimeout)

			workRouter ? request onComplete {
				case Success(response) => replyTo ! response
				case Failure(_: AskTimeoutException) => self.tell(request, replyTo)
				case failure@Failure(_) => replyTo ! ResponseEnvelope.fromRequest(request, failure, HttpStatus.INTERNAL_SERVER_ERROR.value)
			}
	}
}