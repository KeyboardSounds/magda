package au.csiro.data61.magda.registry

import akka.actor.ActorSystem
import akka.stream.Materializer
import au.csiro.data61.magda.opa.OpaTypes._
import com.typesafe.config.Config
import scalikejdbc.DB
import scala.util.{Failure, Success, Try}

import scala.concurrent.{ExecutionContext, Future}
import au.csiro.data61.magda.client.AuthApiClient
import au.csiro.data61.magda.client.HttpFetcher
import java.net.URL

class RegistryAuthApiClient(
    authHttpFetcher: HttpFetcher,
    recordPersistence: RecordPersistence
)(
    implicit config: Config,
    system: ActorSystem,
    ec: ExecutionContext,
    materializer: Materializer
) extends AuthApiClient(authHttpFetcher) {
  def this()(
      implicit config: Config,
      system: ActorSystem,
      executor: ExecutionContext,
      materializer: Materializer
  ) = {
    this(
      HttpFetcher(
        new URL(config.getString("authApi.baseUrl"))
      ),
      DefaultRecordPersistence
    )(
      config,
      system,
      executor,
      materializer
    )
  }
  private def skipOpaQuery(implicit config: Config) =
    config.hasPath("authorization.skipOpaQuery") && config.getBoolean(
      "authorization.skipOpaQuery"
    )

  def queryForRecords(
      jwt: Option[String],
      operationType: AuthOperations.OperationType,
      recordId: Option[String] = None
  ): Future[List[(String, List[List[OpaQuery]])]] = {

    if (skipOpaQuery) {
      Future.successful(List())
    } else {
      val policyIds = DB readOnly { session =>
        recordPersistence.getPolicyIds(session, operationType, recordId)
      } match {
        case Success(Nil) =>
          if (config.hasPath("opa.recordPolicyId")) {
            List(config.getString("opa.recordPolicyId"))
          } else {
            throw new Exception(
              "Error: Missing opa.recordPolicyId in the config."
            )
          }
        case Success(policyIds: List[String]) =>
          policyIds
        case Failure(e: Throwable) => throw e
      }

      super.queryRecord(
        jwt,
        policyIds = policyIds.map(_ + "." + operationType.id)
      )
    }
  }
}
