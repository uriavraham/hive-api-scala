package com.wix.hive.model.contacts

case class Company(role: Option[String] = None, name: Option[String], middle: Option[String] = None)
case class ActivityCompany(role: Option[String] = None, name: Option[String])
