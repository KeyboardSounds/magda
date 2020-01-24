package au.csiro.data61.magda.registry

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.Materializer
import au.csiro.data61.magda.directives.AuthDirectives
import au.csiro.data61.magda.opa.OpaTypes._
import com.typesafe.config.Config

import scala.concurrent.{ExecutionContext, Future}

object Directives {

  def withRecordOpaQuery(
      operationType: AuthOperations.OperationType
  )(
      implicit config: Config,
      system: ActorSystem,
      materializer: Materializer,
      ec: ExecutionContext
  ): Directive1[List[(String, List[List[OpaQuery]])]] = {
    val opaQueryer =
      new RegistryOpaQueryer()(config, system, system.dispatcher, materializer)

    AuthDirectives.getJwt().flatMap { jwt =>
      val recordFuture =
        opaQueryer.queryForRecords(jwt, operationType)

      onSuccess(recordFuture).flatMap { queryResults =>
        provide(queryResults)
      }
    }
  }
}
