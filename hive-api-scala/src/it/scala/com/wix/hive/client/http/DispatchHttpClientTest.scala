package com.wix.hive.client.http

import java.io.InputStream

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{equalTo => equalToString, _}
import com.wix.hive.infrastructure.WiremockEnvironment
import com.wix.hive.json.JacksonObjectMapper
import com.wix.hive.model.WixAPIErrorException
import dispatch.Future
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.{Expectable, MatchResult, Matcher}
import org.specs2.mock.Mockito
import org.specs2.mutable.{Before, SpecificationWithJUnit}
import org.specs2.specification.Scope

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.io.Source
import scala.util.{Failure, Try}

class DispatchHttpClientTest extends SpecificationWithJUnit with Mockito {
  sequential

  step {
    WiremockEnvironment.start()
  }

  trait ctx extends Scope with Before {
    def before = WiremockEnvironment.resetMocks()

    implicit val executionEnv = ExecutionEnv.fromGlobalExecutionContext

    val client = new DispatchHttpClient()

    val baseUrl = s"http://localhost:${WiremockEnvironment.serverPort}"
    val relativeTestUrl = "/test"
    val absoluteTestUrl = baseUrl + relativeTestUrl

    val nonEnglishBodyData = Dummy("גוף")

    val httpRequestData = HttpRequestData(HttpMethod.GET, absoluteTestUrl)

    val executor = mock[ExecutionContextExecutor]
    val clientWithCustomExecutor = new DispatchHttpClient()(executor)

    def beSuccess: Matcher[Future[_]] = (f: Future[_]) => Try(Await.result(f, 1.second)).isSuccess

    def haveDataForDummy(data: String): Matcher[InputStream] =
      (is:InputStream) =>  JacksonObjectMapper.mapper.readValue(is, classOf[Dummy]).data == data

    def asString(obj: AnyRef): String = JacksonObjectMapper.mapper.writeValueAsString(obj)

    def matchJson(json: String) : Matcher[InputStream] = (is: InputStream) => Source.fromInputStream(is).mkString == json

    def isWixApiErrorException(errorCode: Matcher[Int], message: Matcher[Option[String]], wixErrorCode: Matcher[Option[Int]]): Matcher[WixAPIErrorException] = {
      errorCode ^^ { (_: WixAPIErrorException).errorCode aka "errorCode" } and
      message ^^ { (_: WixAPIErrorException).message aka "message" } and
      wixErrorCode ^^ { (_: WixAPIErrorException).wixErrorCode aka "wixErrorCode" }
    }

    def beFailedWith(exception: Matcher[WixAPIErrorException]) = new Matcher[Future[_]] {
      override def apply[S <: Future[_]](t: Expectable[S]): MatchResult[S] = {
        val f = t.value
        Await.ready(f, 1.second).value match {
          case Some(Failure(ex: WixAPIErrorException)) => {
            val res = exception(createExpectable(ex))
            result(res.isSuccess, "", s"Future failed however ${res.message}", t)
          }
          case Some(Failure(ex)) => result(test = false, "", "Not a WixAPIErrorException", t)
          case _ => result(test = false, "", "Future didn't fail", t)
        }
      }
    }
  }

  "request" should {

    "return result matching response from server" in new ctx {
      givenThat(get(urlMatching(relativeTestUrl))
        .willReturn(aResponse().withBody("""{"data":"some info"}""")))

      client.request(httpRequestData) must matchJson("""{"data":"some info"}""").awaitFor(timeout = 1.second)
    }

    "pass query parameters" in new ctx {
      givenThat(get(urlMatching(s"$relativeTestUrl?.*"))
        .willReturn(aResponse()))

      client.request(HttpRequestData(HttpMethod.GET, absoluteTestUrl, queryString = Map("a" -> "b"))) must beSuccess

      verify(getRequestedFor(urlEqualTo(s"$relativeTestUrl?a=b")))
    }

    "pass headers" in new ctx {
      givenThat(get(urlMatching(relativeTestUrl))
        .willReturn(aResponse()))

      client.request(HttpRequestData(HttpMethod.GET, absoluteTestUrl, headers = Map("c" -> "d"))) must beSuccess

      verify(getRequestedFor(urlEqualTo(relativeTestUrl)).withHeader("c", WireMock.equalTo("d")))
    }

    "pass body" in new ctx {
      givenThat(post(urlMatching(relativeTestUrl))
        .willReturn(aResponse()))

      client.request(HttpRequestData(HttpMethod.POST, absoluteTestUrl, body = Some(Dummy("body text")))) must beSuccess

      verify(postRequestedFor(urlEqualTo(relativeTestUrl)).withRequestBody(equalToJson( """{"data":"body text"}""")))
    }

    "work with all method types" in new ctx {
      givenThat(get(urlEqualTo(relativeTestUrl)).willReturn(aResponse().withBody("""{"data":"GET"}""")))
      givenThat(post(urlEqualTo(relativeTestUrl)).willReturn(aResponse().withBody("""{"data":"POST"}""")))
      givenThat(put(urlEqualTo(relativeTestUrl)).willReturn(aResponse().withBody("""{"data":"PUT"}""")))
      givenThat(delete(urlEqualTo(relativeTestUrl)).willReturn(aResponse().withBody("""{"data":"DELETE"}""")))

      HttpMethod.values foreach { method =>
        client.request(HttpRequestData(method, absoluteTestUrl)) must haveDataForDummy(method.toString).await
      }
    }

    "handle error returned from the server" in new ctx {
      givenThat(get(urlEqualTo(relativeTestUrl)).willReturn(aResponse().
                                                    withStatus(400).
                                                    withBody(asString(WixAPIErrorException(400, Some("some msg"), Some(-23001))))))

      val res = client.request(HttpRequestData(HttpMethod.GET, absoluteTestUrl))

      res must beFailedWith(isWixApiErrorException(be_===(400), beSome("some msg"), beSome(-23001)))
    }

    "handle generic 404 error on service/resource not found" in new ctx {
      val res = client.request(HttpRequestData(HttpMethod.GET, absoluteTestUrl + "not"))

      res must beFailedWith(isWixApiErrorException(be_===(404), beSome("Not Found"), beNone))
    }

    "handle 404 error returned from server with response dto" in new ctx {
      givenThat(get(urlEqualTo(relativeTestUrl)).willReturn(aResponse().
        withStatus(404).
        withBody(asString(WixAPIErrorException(404, Some("method not found"), Some(-23007))))))

      val res = client.request(HttpRequestData(HttpMethod.GET, absoluteTestUrl))

      res must beFailedWith(isWixApiErrorException(be_===(404), beSome("method not found"), beSome(-23007)))
    }

    "handle failure - no server listening" in new ctx {
      val res = client.request(HttpRequestData(HttpMethod.GET, absoluteTestUrl)) must beFailedWith(isWixApiErrorException(be_===(404), beSome("Not Found"), beNone))
    }

    "provide other execution context" in new ctx {
      clientWithCustomExecutor.request(httpRequestData)

      eventually {
        there was one(executor).execute(any[Runnable])
      }
    }

    "pass non-English characters in body" in new ctx {
      givenThat(post(urlEqualTo(relativeTestUrl)).willReturn(aResponse()))

      client.request(HttpRequestData(HttpMethod.POST,
        absoluteTestUrl,
        body = Some(nonEnglishBodyData))) must beSuccess

      verify(postRequestedFor(urlEqualTo(relativeTestUrl))
        .withRequestBody(equalToJson(asString(nonEnglishBodyData))))
    }
  }
}

case class Dummy(data: String)