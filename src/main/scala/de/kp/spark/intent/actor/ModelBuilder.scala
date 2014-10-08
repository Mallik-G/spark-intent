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

import akka.actor.{Actor,ActorLogging,ActorRef,Props}

import akka.pattern.ask
import akka.util.Timeout

import de.kp.spark.intent.Configuration

import de.kp.spark.intent.model._
import de.kp.spark.intent.redis.RedisCache

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class ModelBuilder(@transient val sc:SparkContext) extends Actor with ActorLogging {

  implicit val ec = context.dispatcher
  
  private val algorithms = Array(Algorithms.HIDDEN_MARKOV,Algorithms.MARKOV)
  private val sources = Array(Sources.ELASTIC,Sources.FILE,Sources.JDBC,Sources.PIWIK)
  
  def receive = {

    case req:ServiceRequest => {
      
      val origin = sender    
      val uid = req.data("uid")
      
      req.task match {
        
        case "train" => {
          
          val response = validate(req.data) match {
            
            case None => train(req).mapTo[ServiceResponse]            
            case Some(message) => Future {failure(req,message)}
            
          }

          response.onSuccess {
            case result => {
              
              origin ! Serializer.serializeResponse(result)
              context.stop(self)
            
            }
          }

          response.onFailure {
            case throwable => {             
            
              val resp = failure(req,throwable.toString)

              origin ! Serializer.serializeResponse(resp)
              context.stop(self)
            
            }	  
          }
         
        }
       
        case "status" => {

          val resp = if (RedisCache.taskExists(uid) == false) {           
            failure(req,Messages.TASK_DOES_NOT_EXIST(uid))           
            
          } else {   
            status(req)
            
          }
           
          origin ! Serializer.serializeResponse(resp)
          context.stop(self)
           
        }
        
        case _ => {
           
          val msg = Messages.TASK_IS_UNKNOWN(uid,req.task)
          
          origin ! Serializer.serializeResponse(failure(req,msg))
          context.stop(self)
           
        }
        
      }
      
    }
    
    case _ => {
      
      val origin = sender               
      val msg = Messages.REQUEST_IS_UNKNOWN()          
          
      origin ! Serializer.serializeResponse(failure(null,msg))
      context.stop(self)

    }
  
  }
  
  private def train(req:ServiceRequest):Future[Any] = {

    val (duration,retries,time) = Configuration.actor      
    implicit val timeout:Timeout = DurationInt(time).second
    
    ask(actor(req), req)
  
  }

  private def status(req:ServiceRequest):ServiceResponse = {
    
    val uid = req.data("uid")
    val data = Map("uid" -> uid)
                
    new ServiceResponse(req.service,req.task,data,RedisCache.status(uid))	

  }

  private def validate(params:Map[String,String]):Option[String] = {

    val uid = params("uid")
    
    if (RedisCache.taskExists(uid)) {            
      return Some(Messages.TASK_ALREADY_STARTED(uid))   
    }
            
    params.get("algorithm") match {
        
      case None => {
        return Some(Messages.NO_ALGORITHM_PROVIDED(uid))              
      }
        
      case Some(algorithm) => {
        if (algorithms.contains(algorithm) == false) {
          return Some(Messages.ALGORITHM_IS_UNKNOWN(uid,algorithm))    
        }
          
      }
    
    }  
    
    params.get("source") match {
        
      case None => {
        return Some(Messages.NO_SOURCE_PROVIDED(uid))       
      }
        
      case Some(source) => {
        if (sources.contains(source) == false) {
          return Some(Messages.SOURCE_IS_UNKNOWN(uid,source))    
        }          
      }
        
    }

    None
    
  }

  private def actor(req:ServiceRequest):ActorRef = {

    val algorithm = req.data("algorithm")
    if (algorithm == Algorithms.HIDDEN_MARKOV) {  
      context.actorOf(Props(new HiddenMarkovActor(sc)))      
      
    } else {
      context.actorOf(Props(new MarkovActor(sc)))      
    
    }
    
  }

  private def failure(req:ServiceRequest,message:String):ServiceResponse = {
    
    if (req == null) {
      val data = Map("message" -> message)
      new ServiceResponse("","",data,IntentStatus.FAILURE)	
      
    } else {
      val data = Map("uid" -> req.data("uid"), "message" -> message)
      new ServiceResponse(req.service,req.task,data,IntentStatus.FAILURE)	
    
    }
  
  }
  
}