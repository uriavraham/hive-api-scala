package com.wix.hive.client

import com.wix.hive.client.http.HttpMethod.HttpMethod
import com.wix.hive.client.http.{AsyncHttpClient, HttpMethod, NamedParameters}
import com.wix.hive.commands.HiveBaseCommand
import com.wix.hive.matchers.HiveMatchers
import org.specs2.mock.Mockito
import org.specs2.mutable.SpecificationWithJUnit
import org.specs2.specification.Scope


class HiveClientTest extends SpecificationWithJUnit with Mockito with HiveMatchers {

  class Context extends Scope {
    val httpClient = mock[AsyncHttpClient]

    val id = "appId"
    val key = "appKey"
    val instance = "websiteInstance"

    val client = HiveClient(Some(id), Some(key), Some(httpClient))

    def oneCallWithCorrectParams = there was one(httpClient).request(httpRequestDataWith(
      method = be_===(HttpMethod.GET),
      url = be_===(client.baseUrl + client.versionForUrl + commandUrl + commandParams),
      query = havePairs(commandQuery.toSeq :_*),
      headers = headersFor(commandHeaders, client, instance),
      body = be_===(commandBody)))(any)
  }


  "execute" should {

    "call the http client with the correct parameters" in new Context {
      client.execute(instance, TestCommand())

      oneCallWithCorrectParams
    }
  }

  "executeForInsatnce" should {

    "call the http client with the correct parameters" in new Context {
      val executor = client.executeForInstance(instance)

      executor(TestCommand())

      oneCallWithCorrectParams
    }
  }


  "apply" should {
    "load with configuration from conf file" >> {
      val client = HiveClient()

      client.appId must be_===("your app-id here")
      client.baseUrl must be_===("https://openapi.wix.com")
    }
  }


  val commandUrl = "/tst"
  val commandParams = "/param"
  val commandQuery = Map("q" -> "query")
  val commandHeaders = Map("h" -> "header")
  val commandBody = Some(AnyRef)

  case class TestCommand() extends HiveBaseCommand[TestCommandResponse] {
    override def url: String = commandUrl

    override def urlParams: String = commandParams

    override def query: NamedParameters = commandQuery

    override def headers: NamedParameters = commandHeaders

    override def body: Option[AnyRef] = commandBody

    override def method: HttpMethod = HttpMethod.GET
  }

  case class TestCommandResponse()
}

