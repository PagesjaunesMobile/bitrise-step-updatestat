#!/usr/bin/env kscript

//DEPS me.lazmaid.kraph:kraph:0.6.0 com.google.code.gson:gson:2.8.5 log4j:log4j:1.2.14 com.github.kittinunf.fuel:fuel:2.1.0 com.github.kittinunf.fuel:fuel-rxjava:2.1.0



//INCLUDE bash.kt

import org.w3c.dom.Document
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.*
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory
import kotlin.system.exitProcess
import com.github.kittinunf.fuel.*
  
  fun testReq()
  {
    val (request, response, result) = "http://httpbin.org/get"
      .httpGet().response()
      println(response)
      println(result)
    
  }

  testReq()
    
    
