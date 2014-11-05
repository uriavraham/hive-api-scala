package com.wix.hive.commands.contacts

import com.wix.hive.client.http.HttpMethod
import com.wix.hive.matchers.HiveMatchers
import com.wix.hive.model.contacts.ContactName
import org.joda.time.DateTime
import org.specs2.mutable.{SpecificationWithJUnit, Specification}
import org.specs2.specification.Scope

class UpdateNameTest extends SpecificationWithJUnit with HiveMatchers {
  "createHttpRequestData" should {
    "work with parameters" in new Context {
      cmd.createHttpRequestData must httpRequestDataWith(
        method = be_===(HttpMethod.PUT),
        url = contain(contactId) and contain("name"),
        query = havePair("modifiedAt", modifiedAt.toString),
        body = beSome(be_==(name))
      )
    }
  }

  class Context extends ContextForModification {
    val name = ContactName(Some("Mr."), Some("first"), Some("middle"), Some("last"))

    val cmd = UpdateName(contactId, modifiedAt, name)
  }

}
