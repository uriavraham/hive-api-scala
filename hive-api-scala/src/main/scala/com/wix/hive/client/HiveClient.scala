package com.wix.hive.client

import com.typesafe.config._
import com.wix.hive.client.http._
import com.wix.hive.commands.HiveBaseCommand
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat

import scala.concurrent.Future
import scala.reflect.ClassTag

private object DefaultHttpClientFactory {
  def create: AsyncHttpClient = new DispatchHttpClient()
}

class HiveClientSettings(config: Config) {
  def this() = this(ConfigFactory.load())


  config.checkValid(ConfigFactory.defaultReference(), "hive-client")
  val appId = config.getString("hive-client.credentials.appId")
  val appSecret = config.getString("hive-client.credentials.appSecret")
  val baseUrl = config.getString("hive-client.baseUrl")
}

class HiveClient(val appId: String, secretKey: String,
                 httpClient: AsyncHttpClient = DefaultHttpClientFactory.create,
                 val baseUrl: String) {


  def timestamp: String = new DateTime().toString(ISODateTimeFormat.dateTime())

  val version = "1.0.0"

  val versionForUrl = "/v1"

  val agent = s"Hive Scala v$version"

  val signer = new HiveSigner(secretKey)


  def execute[TCommandResult: ClassTag](instanceId: String, command: HiveBaseCommand[TCommandResult]): Future[TCommandResult] = {
    val httpDataFromCommand = command.createHttpRequestData

    val httpDataForRequest = (withClientData(instanceId) _ andThen withSignature andThen withBaseUrl)(httpDataFromCommand)

    httpClient.request(httpDataForRequest)
  }

  def executeForInstance(instanceId: String): (HiveBaseCommand[_] => Future[_]) = this.execute(instanceId, _)


  private def withSignature(httpData: HttpRequestData): HttpRequestData = {
    val signature = signer.getSignature(httpData)
    httpData.copy(headers = httpData.headers + (HiveClient.SignatureKey -> signature))
  }

  private def withClientData(instanceId: String)(httpData: HttpRequestData): HttpRequestData = {
    httpData.copy(
      url = s"$versionForUrl${httpData.url}",
      queryString = httpData.queryString + (HiveClient.VersionKey -> this.version),
      headers = httpData.headers +
        (HiveClient.InstanceIdKey -> instanceId) +
        (HiveClient.ApplicationIdKey -> this.appId) +
        (HiveClient.TimestampKey -> this.timestamp) +
        (HiveClient.UserAgentKey -> this.agent))
  }

  private def withBaseUrl(httpData: HttpRequestData): HttpRequestData = httpData.copy(url = baseUrl + httpData.url)
}

object HiveClient {
  val InstanceIdKey = "x-wix-instance-id"
  val ApplicationIdKey = "x-wix-application-id"
  val TimestampKey = "x-wix-timestamp"
  val SignatureKey = "x-wix-signature"
  val UserAgentKey = "User-Agent"

  val VersionKey = "version"


  def apply(appId: Option[String] = None,
            appSecret: Option[String] = None,
            httpClient: Option[AsyncHttpClient] = None,
            baseUrl: Option[String] = None) = {
    val settings = new HiveClientSettings()

    val id = appId.getOrElse(settings.appId)
    val secret = appSecret.getOrElse(settings.appSecret)
    val url = baseUrl.getOrElse(settings.baseUrl)
    val client = httpClient.getOrElse(DefaultHttpClientFactory.create)

    new HiveClient(id, secret, client ,url)
  }
}