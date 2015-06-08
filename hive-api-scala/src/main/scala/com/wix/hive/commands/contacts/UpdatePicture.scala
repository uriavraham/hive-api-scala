package com.wix.hive.commands.contacts

import com.wix.hive.model.contacts.Contact
import org.joda.time.DateTime

case class UpdatePicture(contactId: String, picture: PictureDTO, modifiedAt: DateTime) extends UpdateContactCommand[Contact] {
  override def urlParams: String = super.urlParams + "/picture"

  override def body: Option[AnyRef] = Some(picture)
}

case class PictureDTO(picture: String)