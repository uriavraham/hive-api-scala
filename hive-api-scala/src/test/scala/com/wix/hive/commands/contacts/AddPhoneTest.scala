package com.wix.hive.commands.contacts

import com.wix.hive.client.http.HttpMethod
import com.wix.hive.matchers.HiveMatchers
import org.joda.time.DateTime
import org.specs2.mutable.{SpecificationWithJUnit, Specification}
import org.specs2.specification.Scope

class AddPhoneTest extends SpecificationWithJUnit with HiveMatchers {
  "createHttpRequestData" should {
    "work with parameters" in new Context {
      cmd.createHttpRequestData must httpRequestDataWith(
        method = be_===(HttpMethod.POST),
        url = contain(contactId) and contain("phone"),
        query = havePair("modifiedAt", modifiedAt.toString),
        body = beSome(be_==(phone))
      )
    }
  }

  class Context extends Scope {
    val contactId = "0a6f6d66-e27c-48ce-9ad0-1fa30977954b"
    val modifiedAt = new DateTime(2010, 3, 2, 1, 2)
    val phone = ContactPhoneDTO("tag-phone", "972-54-5551234")

    val cmd = AddPhone(contactId, modifiedAt, phone)
  }

}
