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

import org.apache.spark.SparkContext
import akka.actor.{ActorRef,Props}

import akka.pattern.ask
import akka.util.Timeout

import akka.actor.{OneForOneStrategy, SupervisorStrategy}
import de.kp.spark.core.model._

import de.kp.spark.intent.Configuration
import de.kp.spark.intent.model._

import scala.concurrent.duration.DurationInt
import scala.concurrent.Future

class IntentMaster(@transient val sc:SparkContext) extends BaseActor {
  
  val (duration,retries,time) = Configuration.actor   

  override val supervisorStrategy = OneForOneStrategy(maxNrOfRetries=retries,withinTimeRange = DurationInt(duration).minutes) {
    case _ : Exception => SupervisorStrategy.Restart
  }
  
  def receive = {
    
    case req:String => {
      
      implicit val ec = context.dispatcher
      implicit val timeout:Timeout = DurationInt(time).second
	  	    
	  val origin = sender

	  val deser = Serializer.deserializeRequest(req)
	  val response = deser.task.split(":")(0) match {

	    case "get" => ask(actor("questor"),deser).mapTo[ServiceResponse]
	    case "index" => ask(actor("indexer"),deser).mapTo[ServiceResponse]

	    case "train"  => ask(actor("builder"),deser).mapTo[ServiceResponse]
	    case "status" => ask(actor("builder"),deser).mapTo[ServiceResponse]

	    case "register" => ask(actor("registrar"),deser).mapTo[ServiceResponse]
	    case "track" => ask(actor("tracker"),deser).mapTo[ServiceResponse]
       
        case _ => {

          Future {     
            failure(deser,Messages.TASK_IS_UNKNOWN(deser.data("uid"),deser.task))
          } 
        
        }
      
      }
      response.onSuccess {
        case result => origin ! Serializer.serializeResponse(result)
      }
      response.onFailure {
        case result => origin ! failure(deser,Messages.GENERAL_ERROR(deser.data("uid")))	      
	  }
      
    }
  
    case _ => {

      val origin = sender               
      val msg = Messages.REQUEST_IS_UNKNOWN()          
          
      origin ! Serializer.serializeResponse(failure(null,msg))

    }
    
  }

  private def actor(worker:String):ActorRef = {
    
    worker match {
  
      case "builder" => context.actorOf(Props(new ModelBuilder(sc)))
        
      case "indexer" => context.actorOf(Props(new IntentIndexer()))
        
      case "questor" => context.actorOf(Props(new ModelQuestor()))
        
      case "registrar" => context.actorOf(Props(new IntentRegistrar()))
        
      case "tracker" => context.actorOf(Props(new IntentTracker()))
      
      case _ => null
      
    }
  
  }

}