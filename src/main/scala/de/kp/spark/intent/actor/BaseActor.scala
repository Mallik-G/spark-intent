package de.kp.spark.intent.actor
/* Copyright (c) 2014 Dr. Krusche & Partner PartG
* 
* This file is part of the Spark-Intent project
* (https://github.com/skrusche63/spark-intent).
* 
* Spark-Intent is free software: you can redistribute it and/or modify it under the
* terms of the GNU General Public License as published by the Free Software
* Foundation, either version 3 of the License, or (at your option) any later
* version.
* 
* Spark-Intent is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
* A PARTICULAR PURPOSE. See the GNU General Public License for more details.
* You should have received a copy of the GNU General Public License along with
* Spark-Intent. 
* 
* If not, see <http://www.gnu.org/licenses/>.
*/

import akka.actor.{Actor,ActorLogging,ActorRef,Props}

import de.kp.spark.core.model._
import de.kp.spark.core.redis.RedisCache

import de.kp.spark.intent.{Configuration,RemoteContext}
import de.kp.spark.intent.model._

abstract class BaseActor extends Actor with ActorLogging {

  val (host,port) = Configuration.redis
  val cache = new RedisCache(host,port.toInt)
  
  protected def failure(req:ServiceRequest,message:String):ServiceResponse = {
    
    if (req == null) {
      val data = Map("message" -> message)
      new ServiceResponse("","",data,IntentStatus.FAILURE)	
      
    } else {
      val data = Map("uid" -> req.data("uid"), "message" -> message)
      new ServiceResponse(req.service,req.task,data,IntentStatus.FAILURE)	
    
    }
    
  }

  /**
   * Notify all registered listeners about a certain status
   */
  protected def notify(req:ServiceRequest,status:String) {

    /* Build message */
    val data = Map("uid" -> req.data("uid"))
    val response = new ServiceResponse(req.service,req.task,data,status)	
    
    /* Notify listeners */
    val message = Serializer.serializeResponse(response)    
    RemoteContext.notify(message)
    
  }
  
  protected def response(req:ServiceRequest,missing:Boolean):ServiceResponse = {
    
    val uid = req.data("uid")
    
    if (missing == true) {
      val data = Map("uid" -> uid, "message" -> Messages.MISSING_INTENT(uid))
      new ServiceResponse(req.service,req.task,data,IntentStatus.FAILURE)	
  
    } else {
      val data = Map("uid" -> uid, "message" -> Messages.MODEL_BUILDING_STARTED(uid))
      new ServiceResponse(req.service,req.task,data,IntentStatus.STARTED)	
  
    }

  }

  protected def serialize(resp:ServiceResponse) = Serializer.serializeResponse(resp)

}