package au.csiro.data61.magda.client

import akka.stream.Materializer
import akka.actor.ActorSystem
import com.typesafe.config.Config
import scala.concurrent.ExecutionContext
import au.csiro.data61.magda.model.Auth.AuthProtocols
import au.csiro.data61.magda.model.Auth.User
import java.net.URL
import akka.http.scaladsl.Http
import scala.concurrent.Future
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.RawHeader
import au.csiro.data61.magda.opa._
import au.csiro.data61.magda.opa.OpaTypes.OpaQuery
import akka.http.scaladsl.model._
import spray.json._
import akka.util.ByteString
import scala.concurrent.duration._

class AuthApiClient(authHttpFetcher: HttpFetcher)(
    implicit val config: Config,
    implicit val system: ActorSystem,
    implicit val executor: ExecutionContext,
    implicit val materializer: Materializer
) extends AuthProtocols {

  // private val opaUrl: String = config.getConfig("opa").getString("baseUrl")
  private val logger = system.log

  def this()(
      implicit config: Config,
      system: ActorSystem,
      executor: ExecutionContext,
      materializer: Materializer
  ) = {
    this(HttpFetcher(new URL(config.getString("authApi.baseUrl"))))(
      config,
      system,
      executor,
      materializer
    )
  }

  def getUserPublic(userId: String): Future[User] = {
    val responseFuture = authHttpFetcher.get(s"/v0/public/users/$userId")

    responseFuture.flatMap(
      response =>
        response.status match {
          case StatusCodes.OK => Unmarshal(response.entity).to[User]
          case _ =>
            Unmarshal(response.entity)
              .to[String]
              .map(error => throw new Exception(error))
        }
    )
  }

  def queryRecord(
      jwtToken: Option[String],
      operationType: AuthOperations.OperationType,
      policyIds: List[String]
  ): Future[List[(String, List[List[OpaTypes.OpaQuery]])]] = {
    Future.sequence(
      policyIds.map(
        policyId =>
          queryPolicy(jwtToken, operationType, policyId).map(
            result => (policyId, result)
          )
      )
    )
  }

  private def queryPolicy(
      jwtToken: Option[String],
      operationType: AuthOperations.OperationType,
      policyId: String
  ): Future[List[List[OpaQuery]]] = {
    val requestData: String = s"""{
                                 |  "query": "data.$policyId.${operationType.id}",
                                 |  "unknowns": ["input.object"]
                                 |}""".stripMargin

    val headers = jwtToken match {
      case Some(jwt) => List(RawHeader("X-Magda-Session", jwt))
      case None      => List()
    }

    // val httpRequest = HttpRequest(
    //   uri = s"$opaUrl/compile",
    //   method = HttpMethods.POST,
    //   entity = HttpEntity(ContentTypes.`application/json`, requestData),
    //   headers = headers
    // )

    // Http()
    //   .singleRequest(httpRequest)
    //   .flatMap(receiveOpaResponse[List[List[OpaQuery]]](_) { json =>
    //     parseOpaResponse(json)
    //   })

    authHttpFetcher
      .post(
        s"/opa/compile",
        HttpEntity(ContentTypes.`application/json`, requestData),
        headers
      )
      .flatMap(receiveOpaResponse[List[List[OpaQuery]]](_) { json =>
        OpaParser.parseOpaResponse(json)
      })
  }

  private def receiveOpaResponse[T](
      res: HttpResponse
  )(fn: JsValue => T): Future[T] = {
    if (res.status.intValue() != 200) {
      res.entity.dataBytes.runFold(ByteString(""))(_ ++ _).flatMap { body =>
        logger
          .error(s"OPA failed to process the request: {}", body.utf8String)
        Future.failed(
          new Exception(
            s"Failed to retrieve access control decision from OPA: ${body.utf8String}"
          )
        )
      }
    } else {
      res.entity.toStrict(10.seconds).map { entity =>
        fn(entity.data.utf8String.parseJson)
      }
    }
  }
}
