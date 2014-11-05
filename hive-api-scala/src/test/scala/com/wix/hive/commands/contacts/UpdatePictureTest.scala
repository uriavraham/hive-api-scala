package com.wix.hive.commands.contacts

import com.wix.hive.client.http.HttpMethod
import com.wix.hive.matchers.HiveMatchers
import com.wix.hive.model.contacts.ContactName
import org.joda.time.DateTime
import org.specs2.mutable.{SpecificationWithJUnit, Specification}
import org.specs2.specification.Scope

class UpdatePictureTest extends SpecificationWithJUnit with HiveMatchers {
  "createHttpRequestData" should {
    "work with parameters" in new Context {
      cmd.createHttpRequestData must httpRequestDataWith(
        method = be_===(HttpMethod.PUT),
        url = contain(contactId) and contain("picture"),
        query = havePair("modifiedAt", modifiedAt.toString),
        body = beSome(be_==(picture))
      )
    }
  }

  class Context extends ContextForModification{
    val picture = PictureDTO("some-pic-name")

    val cmd = UpdatePicture(contactId, modifiedAt, picture)
  }

}
